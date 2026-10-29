package com.lagradost.shiro.utils

import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers (for the current session) which of the user's sources keep failing, so the UI can say so instead of
 * leaving people guessing. Keys look like "cs:<provider>", "zg:<id>" and "ay:<id>".
 */
object SourceHealth {
    private val streaks = ConcurrentHashMap<String, Int>()

    fun ok(key: String) {
        streaks[key] = 0
    }

    fun fail(key: String) {
        streaks[key] = (streaks[key] ?: 0) + 1
    }

    fun isFailing(key: String) = (streaks[key] ?: 0) >= 2

    fun label(label: String, key: String) = if (isFailing(key)) "$label  (not responding)" else label
}
