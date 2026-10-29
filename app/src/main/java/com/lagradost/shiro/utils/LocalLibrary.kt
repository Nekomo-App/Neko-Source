package com.lagradost.shiro.utils

import DataStore.getKey
import DataStore.setKey
import android.content.Context
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.shiro.utils.AniListApi.Companion.AniListStatusType
import com.lagradost.shiro.utils.AniListApi.Companion.postDataAboutId

/**
 * Offline library for people who are not signed in. Everything is stored on the device; it can be exported to a
 * JSON file/text, imported on another device, and pushed to an AniList account after signing in.
 */
object LocalLibrary {
    private const val KEY = "local_library_v1"

    val STATUSES = listOf("Watching", "Plan to watch", "Completed", "On hold", "Dropped")

    data class Entry(
        @JsonProperty("slug") val slug: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("poster") val poster: String? = null,
        @JsonProperty("status") val status: String = STATUSES[0],
        @JsonProperty("updatedAt") val updatedAt: Long = System.currentTimeMillis(),
        @JsonProperty("progress") val progress: Int = 0,
    )

    private val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    @Synchronized
    fun all(context: Context): List<Entry> =
        (context.getKey<Array<Entry>>(KEY)?.toList() ?: emptyList()).sortedByDescending { it.updatedAt }

    fun statusOf(context: Context, slug: String): String? = all(context).firstOrNull { it.slug == slug }?.status

    @Synchronized
    fun set(context: Context, slug: String, title: String, poster: String?, status: String) {
        val cur = all(context).filter { it.slug != slug }
        val old = all(context).firstOrNull { it.slug == slug }
        context.setKey(KEY, cur + Entry(slug, title, poster, status, System.currentTimeMillis(), old?.progress ?: 0))
    }

    @Synchronized
    fun remove(context: Context, slug: String) {
        context.setKey(KEY, all(context).filter { it.slug != slug })
    }

    fun exportJson(context: Context): String = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(
        mapOf("app" to "Nekomo", "type" to "local-library", "version" to 1, "entries" to all(context))
    )

    /** Merges entries from [json] (newest update wins). Returns how many entries were added or updated. */
    @Synchronized
    fun importJson(context: Context, json: String): Int {
        val node = mapper.readTree(json)
        val incoming = mapper.readValue<List<Entry>>(node.path("entries").toString())
        val merged = all(context).associateBy { it.slug }.toMutableMap()
        var changed = 0
        for (e in incoming) {
            val mine = merged[e.slug]
            if (mine == null || e.updatedAt > mine.updatedAt) {
                merged[e.slug] = e
                changed++
            }
        }
        context.setKey(KEY, merged.values.toList())
        return changed
    }

    private fun toAniListStatus(status: String) = when (status) {
        "Completed" -> AniListStatusType.Completed
        "On hold" -> AniListStatusType.Paused
        "Dropped" -> AniListStatusType.Dropped
        "Plan to watch" -> AniListStatusType.Planning
        else -> AniListStatusType.Watching
    }

    /** Pushes entries that came from the AniList catalogue (slug "al<id>") to the signed-in AniList account. */
    fun syncToAniList(context: Context): Pair<Int, Int> {
        var ok = 0
        var skipped = 0
        for (e in all(context)) {
            val id = Regex("^al(\\d+)$").find(e.slug)?.groupValues?.get(1)?.toIntOrNull()
            if (id == null) {
                skipped++
                continue
            }
            if (context.postDataAboutId(id, toAniListStatus(e.status), 0, e.progress)) ok++ else skipped++
        }
        return ok to skipped
    }
}
