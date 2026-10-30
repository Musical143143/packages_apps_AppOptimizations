package com.android.appoptimization

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.IPackageDataObserver
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserHandle
import android.os.storage.StorageManager
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class AppCache(val packageName: String, val label: String, val cacheBytes: Long)

/**
 * Finds and clears per-app cache ("junk"). Uses the system cache-clear API, which is
 * why this app must be a privileged system app with CLEAR_APP_CACHE.
 * Blocking methods: call them off the main thread.
 */
class JunkCleaner(private val context: Context) {
    private val pm: PackageManager = context.packageManager
    private val storageStats = context.getSystemService(StorageStatsManager::class.java)

    /** Apps with non-zero cache, largest first. */
    fun scan(): List<AppCache> {
        val user = Process.myUserHandle()
        return pm.getInstalledApplications(0)
            .mapNotNull { info ->
                val bytes = cacheBytesOf(info, user) ?: return@mapNotNull null
                if (bytes <= 0L) null
                else AppCache(info.packageName, info.loadLabel(pm).toString(), bytes)
            }
            .sortedByDescending { it.cacheBytes }
    }

    /**
     * Clears the cache of each package. [onProgress] receives a value from 0 to 1 as each
     * package finishes. It can be called from a system thread. Returns how many failed.
     */
    fun clean(packages: List<String>, onProgress: (Float) -> Unit = {}): Int {
        val total = packages.size
        val latch = CountDownLatch(total)
        val failed = AtomicInteger(0)
        val done = AtomicInteger(0)
        packages.forEach { pkg ->
            pm.deleteApplicationCacheFiles(pkg, object : IPackageDataObserver.Stub() {
                override fun onRemoveCompleted(packageName: String?, succeeded: Boolean) {
                    if (!succeeded) failed.incrementAndGet()
                    onProgress(done.incrementAndGet().toFloat() / total)
                    latch.countDown()
                }
            })
        }
        // Callbacks can be lost if a package is busy; don't wait forever.
        latch.await(CLEAN_TIMEOUT_SEC, TimeUnit.SECONDS)
        clearOwnCache()
        return failed.get() + latch.count.toInt()
    }

    private fun cacheBytesOf(info: ApplicationInfo, user: UserHandle): Long? = runCatching {
        val uuid = info.volumeUuid?.let { UUID.fromString(it) } ?: StorageManager.UUID_DEFAULT
        storageStats.queryStatsForPackage(uuid, info.packageName, user).cacheBytes
    }.getOrNull()

    private fun clearOwnCache() {
        context.cacheDir.listFiles()?.forEach { file ->
            runCatching { file.deleteRecursively() }
        }
    }

    private companion object {
        const val CLEAN_TIMEOUT_SEC = 30L
    }
}
