package com.android.appoptimization

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class MemorySnapshot(val availBytes: Long, val totalBytes: Long) {
    val usedFraction: Float
        get() = if (totalBytes <= 0L) 0f
        else ((totalBytes - availBytes).toFloat() / totalBytes).coerceIn(0f, 1f)
}

/** A background app and how much memory its processes use (proportional set size). */
data class ProcessUsage(val packageName: String, val label: String, val pssBytes: Long)

/**
 * Frees RAM by stopping background (not visible, not perceptible) processes.
 * Foreground apps, the home screen, and core system UI are never touched.
 * Blocking methods: call them off the main thread.
 */
class RamBooster(private val context: Context) {
    private val am = context.getSystemService(ActivityManager::class.java)
    private val pm: PackageManager = context.packageManager

    private val exempt: Set<String> = buildSet {
        add(context.packageName)
        add("com.android.systemui")
        add("com.android.phone")
        add("com.android.settings")
        // Never kill the home screen; ask the system which app is the default launcher.
        pm.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName?.let { add(it) }
    }

    fun snapshot(): MemorySnapshot {
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return MemorySnapshot(availBytes = info.availMem, totalBytes = info.totalMem)
    }

    /**
     * Stops every background package that isn't exempt. [onProgress] receives a value from
     * 0 to 1 as each package is stopped. Returns how many were asked to stop.
     */
    fun boost(onProgress: (Float) -> Unit = {}): Int {
        val targets = backgroundPackages()
        targets.forEachIndexed { index, pkg ->
            am.killBackgroundProcesses(pkg)
            onProgress((index + 1).toFloat() / targets.size)
        }
        return targets.size
    }

    /** Stops the background processes of one package. */
    fun stop(packageName: String) {
        if (packageName !in exempt) am.killBackgroundProcesses(packageName)
    }

    /** Background apps using the most memory, largest first. */
    fun topProcesses(limit: Int = 5): List<ProcessUsage> {
        val procs = am.runningAppProcesses.orEmpty()
            .filter { it.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE }
        if (procs.isEmpty()) return emptyList()

        val memory = am.getProcessMemoryInfo(procs.map { it.pid }.toIntArray()).toList()
        return procs.zip(memory)
            .mapNotNull { (proc, mem) ->
                val pkg = proc.pkgList?.firstOrNull() ?: return@mapNotNull null
                if (pkg in exempt) null else pkg to mem.totalPss.toLong() * 1024L
            }
            .groupBy({ it.first }, { it.second })
            .map { (pkg, sizes) -> ProcessUsage(pkg, labelOf(pkg), sizes.sum()) }
            .sortedByDescending { it.pssBytes }
            .take(limit)
    }

    private fun backgroundPackages(): List<String> = am.runningAppProcesses.orEmpty()
        .filter { it.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE }
        .flatMap { it.pkgList?.toList().orEmpty() }
        .distinct()
        .filterNot { it in exempt }

    private fun labelOf(packageName: String): String = runCatching {
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    }.getOrDefault(packageName)
}
