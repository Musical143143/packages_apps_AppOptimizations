package com.android.appoptimization

import android.content.Context

/** A finished "Optimise everything" run, shown in the history card. */
data class RunRecord(
    val timeMillis: Long,
    val junkFreed: Long,
    val appsStopped: Int,
    val ramFreed: Long,
)

/** Saved settings and history, kept in this app's private SharedPreferences. */
class OptimizerPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** Packages the user asked to keep running. Boost never stops these. */
    fun protectedApps(): Set<String> =
        prefs.getStringSet(KEY_PROTECTED, emptySet()).orEmpty().toSet()

    /** Adds or removes [packageName] from the protected list. Returns the new list. */
    fun setProtected(packageName: String, protected: Boolean): Set<String> {
        // Copy the set: SharedPreferences must not be given a set that is later mutated.
        val updated = protectedApps().toMutableSet()
        if (protected) updated.add(packageName) else updated.remove(packageName)
        prefs.edit().putStringSet(KEY_PROTECTED, updated).apply()
        return updated
    }

    fun lastRun(): RunRecord? {
        val time = prefs.getLong(KEY_RUN_TIME, 0L)
        if (time == 0L) return null
        return RunRecord(
            timeMillis = time,
            junkFreed = prefs.getLong(KEY_JUNK_FREED, 0L),
            appsStopped = prefs.getInt(KEY_APPS_STOPPED, 0),
            ramFreed = prefs.getLong(KEY_RAM_FREED, 0L),
        )
    }

    fun saveRun(run: RunRecord) {
        prefs.edit()
            .putLong(KEY_RUN_TIME, run.timeMillis)
            .putLong(KEY_JUNK_FREED, run.junkFreed)
            .putInt(KEY_APPS_STOPPED, run.appsStopped)
            .putLong(KEY_RAM_FREED, run.ramFreed)
            .apply()
    }

    /** Index into AccentColors. */
    fun accentIndex(): Int = prefs.getInt(KEY_ACCENT, 0).coerceIn(0, AccentColors.lastIndex)

    fun setAccentIndex(index: Int) {
        prefs.edit().putInt(KEY_ACCENT, index.coerceIn(0, AccentColors.lastIndex)).apply()
    }

    /** When on, Boost runs once each time the app opens and memory is above the threshold. */
    fun autoBoostEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_BOOST, false)

    fun setAutoBoostEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BOOST, enabled).apply()
    }

    /** Memory use (0 to 1) at which auto-boost runs. */
    fun autoBoostThreshold(): Float = prefs.getFloat(KEY_THRESHOLD, 0.85f)

    fun setAutoBoostThreshold(threshold: Float) {
        prefs.edit().putFloat(KEY_THRESHOLD, threshold).apply()
    }

    private companion object {
        const val FILE_NAME = "app_optimization"
        const val KEY_PROTECTED = "protected_apps"
        const val KEY_RUN_TIME = "last_run_time"
        const val KEY_JUNK_FREED = "last_junk_freed"
        const val KEY_APPS_STOPPED = "last_apps_stopped"
        const val KEY_RAM_FREED = "last_ram_freed"
        const val KEY_ACCENT = "accent_index"
        const val KEY_AUTO_BOOST = "auto_boost"
        const val KEY_THRESHOLD = "auto_boost_threshold"
    }
}
