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

/**
 * Frees RAM by stopping background (not visible, not perceptible) processes.
 * Foreground apps, the home screen, and core system UI are never touched.
 * Blocking methods: call them off the main thread.
 */
class RamBooster(private val context: Context) {
    private val am = context.getSystemService(ActivityManager::class.java)

    private val exempt: Set<String> = buildSet {
        add(context.packageName)
        add("com.android.systemui")
        add("com.android.phone")
        add("com.android.settings")
        // Never kill the home screen; ask the system which app is the default launcher.
        context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName?.let { add(it) }
    }

    fun snapshot(): MemorySnapshot {
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return MemorySnapshot(availBytes = info.availMem, totalBytes = info.totalMem)
    }

    /** Stops background processes. Returns the number of packages asked to stop. */
    fun boost(): Int {
        val targets = am.runningAppProcesses.orEmpty()
            .filter { it.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE }
            .flatMap { it.pkgList?.toList().orEmpty() }
            .distinct()
            .filterNot { it in exempt }
        targets.forEach { am.killBackgroundProcesses(it) }
        return targets.size
    }
}
