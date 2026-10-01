package com.lagradost.shiro.utils.auth

import ANILIST_TOKEN_KEY
import ANILIST_USER_KEY
import DataStore.getKey
import DataStore.getKeys
import DataStore.removeKey
import DataStore.setKey
import MAL_TOKEN_KEY
import MAL_USER_KEY
import android.content.Context
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.lagradost.shiro.utils.ANILIST_ACCOUNT_ID
import com.lagradost.shiro.utils.MAL_ACCOUNT_ID

const val KITSU_TOKEN_KEY = "kitsu_token"
const val KITSU_REFRESH_TOKEN_KEY = "kitsu_refresh_token"
const val KITSU_USER_KEY = "kitsu_user"
const val KITSU_ACCOUNT_ID = "0"
private const val RULES_ACCEPTED_KEY = "community_rules_accepted"

/**
 * Sign-in gate state. A user counts as signed in only when a token AND a fetched
 * profile exist for at least one provider, so a half-finished login (token stored,
 * profile fetch failed) does not let anybody into the app.
 */
object AuthManager {
    val mapper: JsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    private fun Context.hasAniList() =
        getKey<String>(ANILIST_TOKEN_KEY, ANILIST_ACCOUNT_ID, null) != null &&
                getKeys(ANILIST_USER_KEY).isNotEmpty()

    private fun Context.hasMal() =
        getKey<String>(MAL_TOKEN_KEY, MAL_ACCOUNT_ID, null) != null &&
                getKeys(MAL_USER_KEY).isNotEmpty()

    private fun Context.hasKitsu() =
        getKey<String>(KITSU_TOKEN_KEY, KITSU_ACCOUNT_ID, null) != null &&
                getKeys(KITSU_USER_KEY).isNotEmpty()

    fun isSignedIn(context: Context): Boolean = try {
        context.hasAniList() || context.hasMal() || context.hasKitsu()
    } catch (e: Exception) {
        false
    }

    fun hasAcceptedRules(context: Context): Boolean =
        context.getKey<Boolean>(RULES_ACCEPTED_KEY, false) ?: false

    fun setRulesAccepted(context: Context) = context.setKey(RULES_ACCEPTED_KEY, true)

    fun logoutKitsu(context: Context) {
        context.removeKey(KITSU_TOKEN_KEY, KITSU_ACCOUNT_ID)
        context.removeKey(KITSU_REFRESH_TOKEN_KEY, KITSU_ACCOUNT_ID)
        context.removeKey(KITSU_USER_KEY, KITSU_ACCOUNT_ID)
    }

    fun isLoggedIntoKitsu(context: Context) = context.hasKitsu()
}
