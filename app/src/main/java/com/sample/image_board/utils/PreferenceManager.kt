package com.sample.image_board.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * PreferenceManager untuk manage user preferences Menggunakan SharedPreferences untuk simplicity
 */
object PreferenceManager {

    private const val PREF_NAME = "imgr_preferences"
    private const val KEY_STAY_LOGGED_IN = "stay_logged_in"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /** Get stay logged in preference Default: true (user stays logged in by default) */
    fun getStayLoggedIn(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_STAY_LOGGED_IN, true)
    }

    /** Set stay logged in preference */
    fun setStayLoggedIn(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_STAY_LOGGED_IN, enabled).apply()
    }
}
