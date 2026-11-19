package com.lagradost.shiro.utils

import android.app.Activity
import android.content.Context
import android.view.WindowManager
import androidx.preference.PreferenceManager

/** "Keep screen on while using the app" (Settings -> Appearance). The player keeps the
 *  screen on during playback regardless. */
object KeepScreenOn {
    const val KEY = "keep_screen_on"

    private fun enabled(c: Context) =
        PreferenceManager.getDefaultSharedPreferences(c).getBoolean(KEY, false)

    fun apply(a: Activity) {
        if (enabled(a)) a.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else a.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
