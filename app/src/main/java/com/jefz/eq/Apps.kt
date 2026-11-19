package com.jefz.eq

import android.content.Intent
import android.content.pm.PackageManager

/** Launcher-app list, loaded once off the main thread (fixes lag when opening the FX tab). */
object Apps {
    @Volatile var list: List<Pair<String, String>> = emptyList()
    @Volatile var loaded = false

    @Synchronized
    fun load(pm: PackageManager) {
        if (loaded) return
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        list = pm.queryIntentActivities(i, 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
        loaded = true
    }
}
