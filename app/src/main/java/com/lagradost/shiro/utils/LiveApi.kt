package com.lagradost.shiro.utils

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.lagradost.shiro.utils.cs3.CsBridge
import com.lagradost.shiro.utils.mvvm.logError
import kotlin.random.Random as KotlinRandom

/**
 * Metadata catalogue backed by AniList GraphQL (graphql.anilist.co): trending, recents, search, details.
 *
 * Nekomo bundles NO streaming source. Playable content only comes from sources the user installs and
 * enables themselves (CloudStream extensions, Zangetsu sources, Aniyomi extensions); their slugs/tokens
 * ("cs3-..", "zg-..", "ay-..") are routed to the matching bridge.
 *
 * AniList slugs are "al<anilistId>"; a "-dub" suffix is stripped for compatibility with old saved data.
 */
object LiveApi {
    private const val ANILIST_GQL = "https://graphql.anilist.co"

    private val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

    private val defaultHeaders get() = mapOf("User-Agent" to ShiroApi.USER_AGENT)

    // region ---------- HTTP helpers ----------

    private fun gql(url: String, query: String, variables: Map<String, Any?>): JsonNode? {
        return try {
            // Plain JDK map: Jackson + kotlin-reflect fails on kotlin.collections.EmptyMap after R8
            val body = mapper.writeValueAsString(
                linkedMapOf<String, Any?>("query" to query, "variables" to LinkedHashMap(variables))
            )
            val headers =
                defaultHeaders
            val res = khttp.post(url, headers = headers, json = body, timeout = 30.0)
            if (res.statusCode != 200) return null
            mapper.readTree(res.text)
        } catch (e: Exception) {
            logError(e)
            null
        }
    }

    private fun anilist(query: String, variables: Map<String, Any?>): JsonNode? =
        gql(ANILIST_GQL, query, variables)?.path("data")

    // endregion

    // region ---------- GraphQL documents ----------

    private const val MEDIA_FIELDS = """
        id idMal siteUrl
        title { romaji english native }
        coverImage { large extraLarge }
        bannerImage
        description(asHtml: false)
        genres format seasonYear status averageScore episodes duration
        startDate { year month day }
        nextAiringEpisode { episode }
    """

    private const val TRENDING_QUERY = """
        query {
            Page(page: 1, perPage: 24) {
                media(sort: TRENDING_DESC, type: ANIME, isAdult: false) { $MEDIA_FIELDS }
            }
        }
    """

    private const val RECENTS_QUERY = """
        query {
            Page(page: 1, perPage: 30) {
                airingSchedules(sort: TIME_DESC, notYetAired: true) {
                    episode
                    media { $MEDIA_FIELDS }
                }
            }
        }
    """

    private const val RANDOM_QUERY = """
        query(${"$"}page: Int) {
            Page(page: ${"$"}page, perPage: 50) {
                media(sort: POPULARITY_DESC, type: ANIME, isAdult: false) { $MEDIA_FIELDS }
            }
        }
    """

    private const val MEDIA_BY_ID_QUERY = """
        query(${"$"}id: Int) { Media(id: ${"$"}id, type: ANIME) { $MEDIA_FIELDS } }
    """

    private const val MEDIA_BY_MAL_QUERY = """
        query(${"$"}id: Int) { Media(idMal: ${"$"}id, type: ANIME) { $MEDIA_FIELDS } }
    """

    private const val SEARCH_QUERY = """
        query(${"$"}q: String, ${"$"}genres: [String]) {
            Page(page: 1, perPage: 40) {
                media(search: ${"$"}q, type: ANIME, genre_in: ${"$"}genres, isAdult: false) { $MEDIA_FIELDS }
            }
        }
    """

    private const val GENRES_QUERY = "query { GenreCollection }"

    // endregion

    // region ---------- mapping helpers ----------

    private fun JsonNode?.txt(vararg path: String): String {
        var node = this ?: return ""
        for (p in path) node = node.path(p)
        return if (node.isMissingNode || node.isNull) "" else node.asText("")
    }

    private fun normTitle(s: String?): String =
        (s ?: "").lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun mediaSlug(media: JsonNode): String = "al${media.path("id").asInt()}"

    private fun mediaToData(media: JsonNode, slugOverride: String? = null): ShiroApi.Companion.Data {
        val mal = media.path("idMal").takeIf { !it.isNull }?.asInt()
        val al = media.path("id").asInt()
        val start = media.path("startDate")
        val aired = if (start.path("year").isMissingNode || start.path("year").isNull) ""
        else "${start.path("year").asInt()}-${start.path("month").asInt()}-${start.path("day").asInt()}"
        return ShiroApi.Companion.Data(
            id = al.toString(),
            slug = slugOverride ?: mediaSlug(media),
            title = media.txt("title", "romaji").ifEmpty { media.txt("title", "english") },
            title_english = media.txt("title", "english").ifEmpty { null },
            native_title = media.txt("title", "native"),
            poster = media.txt("coverImage", "extraLarge").ifEmpty { media.txt("coverImage", "large") },
            banner = media.txt("bannerImage"),
            ids = mapper.writeValueAsString(
                mapOf("mal" to mal?.toString(), "anilist" to al.toString())
            ),
            type = "anime",
            format = media.txt("format"),
            episodes = media.txt("episodes"),
            episode_duration = media.txt("duration"),
            synopsis = media.txt("description")
                .replace(Regex("<br\\s*/?>"), "\n")
                .replace(Regex("<[^>]*>"), ""),
            language = "sub",
            synonyms = "",
            season = "",
            release_year = media.txt("seasonYear").ifEmpty { null },
            score = media.txt("averageScore"),
            rating = "",
            studios = "",
            genres = media.path("genres").mapNotNull { it.asText(null) }.joinToString(","),
            aired = aired,
            status = media.txt("status"),
            trailer = "",
            total_views = "0",
            created_at = "",
            updated_at = ""
        )
    }

    // endregion

    // region ---------- catalog ----------

    fun getTrending(): ShiroApi.Companion.ShowHolder? {
        val data = anilist(TRENDING_QUERY, emptyMap()) ?: return null
        val list = data.path("Page").path("media").map { mediaToData(it) }
        return ShiroApi.Companion.ShowHolder("success", "", list)
    }

    fun getRandom(): ShiroApi.Companion.Random? {
        val page = KotlinRandom.nextInt(1, 8)
        val data = anilist(RANDOM_QUERY, mapOf("page" to page)) ?: return null
        val media = data.path("Page").path("media")
        if (media.size() == 0) return null
        return ShiroApi.Companion.Random(
            "success", "", mediaToData(media[KotlinRandom.nextInt(0, media.size())])
        )
    }

    fun getRecents(): List<ShiroApi.Companion.AnimeHolder>? {
        val data = anilist(RECENTS_QUERY, emptyMap()) ?: return null
        val holders = mutableListOf<ShiroApi.Companion.AnimeHolder>()
        val seen = HashSet<String>()
        data.path("Page").path("airingSchedules").forEach { sched ->
            val media = sched.path("media")
            val ep = sched.path("episode").asInt()
            val slug = mediaSlug(media)
            if (ep <= 0 || !seen.add("$slug-$ep")) return@forEach
            val d = mediaToData(media)
            holders.add(
                ShiroApi.Companion.AnimeHolder(
                    anime = ShiroApi.Companion.Anime(
                        title = d.title,
                        slug = slug,
                        poster = d.poster,
                        synopsis = d.synopsis,
                        format = d.format
                    ),
                    episode = ShiroApi.Companion.Episode(
                        id = "$slug-$ep",
                        slug = slug,
                        title = "",
                        episode = ep.toString(),
                        image = d.poster,
                        insight = null,
                        sources = "[{\"slug\":\"gogostream\",\"source\":\"al${media.path("id").asInt()}|$ep|sub\"}]",
                        ext = null,
                        views = "0",
                        created_at = "",
                        updated_at = ""
                    )
                )
            )
        }
        return holders
    }

    fun getGenres(): List<String>? {
        val data = anilist(GENRES_QUERY, emptyMap()) ?: return null
        return data.path("GenreCollection").mapNotNull { it.asText(null) }
    }

    // endregion

    // region ---------- search ----------

    fun search(query: String, genres: List<String>?): List<ShiroApi.Companion.Data>? {
        val data = anilist(SEARCH_QUERY, mapOf("q" to query, "genres" to (genres?.takeIf { it.isNotEmpty() })))
            ?: return null
        return data.path("Page").path("media").map { mediaToData(it) }
    }

    // endregion

    // region ---------- anime page ----------

    private fun anilistMediaById(id: Int): JsonNode? =
        anilist(MEDIA_BY_ID_QUERY, mapOf("id" to id))?.path("Media")

    private fun anilistMediaByMal(id: Int): JsonNode? =
        anilist(MEDIA_BY_MAL_QUERY, mapOf("id" to id))?.path("Media")


    /**
     * Metadata-only page for an AniList title. Streams come from sources the user installs, so the episode
     * list is empty here; the page's "Choose source" button finds the title in an installed source.
     */
    fun getAnimePage(slug: String): ShiroApi.Companion.AnimePageNewRoot? {
        val bare = slug.removeSuffix("-dub")

        // Slugs from user-installed sources are self-contained tokens
        if (CsBridge.isCs3Slug(bare)) return CsBridge.getAnimePage(bare)
        if (com.lagradost.shiro.utils.zg.ZangetsuBridge.isZgSlug(bare)) return com.lagradost.shiro.utils.zg.ZangetsuBridge.getAnimePage(bare)
        if (com.lagradost.shiro.utils.aniyomi.AniyomiBridge.isAySlug(bare)) return com.lagradost.shiro.utils.aniyomi.AniyomiBridge.getAnimePage(bare)

        return try {
            val alId = bare.removePrefix("al").toIntOrNull() ?: return null
            val media = anilistMediaById(alId) ?: return null
            val d = mediaToData(media, slugOverride = slug)
            ShiroApi.Companion.AnimePageNewRoot(
                "success", "",
                ShiroApi.Companion.AnimePageNewData(anime = dataToAnimePage(d, slug), episodes = emptyList())
            )
        } catch (e: Exception) {
            logError(e)
            null
        }
    }

    private fun anilistSearchMedia(titles: List<String>): JsonNode? {
        for (t in titles.filter { it.isNotBlank() }) {
            val data = anilist(
                """
                query(${"$"}q: String) {
                    Page(perPage: 5) { media(search: ${"$"}q, type: ANIME) { $MEDIA_FIELDS } }
                }
                """,
                mapOf("q" to t)
            ) ?: continue
            val list = data.path("Page").path("media")
            val norm = normTitle(t)
            val match = list.firstOrNull {
                normTitle(it.txt("title", "romaji")) == norm ||
                        normTitle(it.txt("title", "english")) == norm
            } ?: list.firstOrNull()
            if (match != null) return match
        }
        return null
    }

    fun getAnimePageByMal(malId: String): ShiroApi.Companion.AnimePageNewRoot? {
        val media = malId.toIntOrNull()?.let { anilistMediaByMal(it) } ?: return null
        val alId = media.path("id").asInt()
        return getAnimePage("al$alId")
    }

    private fun dataToAnimePage(d: ShiroApi.Companion.Data, slug: String): ShiroApi.Companion.AnimePageNew {
        return ShiroApi.Companion.AnimePageNew(
            id = d.id, slug = slug, title = d.title, title_english = d.title_english,
            native_title = d.native_title, poster = d.poster, banner = d.banner,
            ids = d.ids, type = d.type, format = d.format, episodes = d.episodes,
            episode_duration = d.episode_duration, synopsis = d.synopsis,
            language = d.language, synonyms = d.synonyms, season = d.season,
            release_year = d.release_year, score = d.score, rating = d.rating,
            studios = d.studios, genres = d.genres, aired = d.aired, status = d.status,
            trailer = d.trailer, total_views = d.total_views,
            created_at = d.created_at, updated_at = d.updated_at
        )
    }

    // endregion

    // region ---------- stream resolution ----------

    /**
     * Nekomo ships no streaming source of its own. Episode tokens only exist for content that comes from
     * sources the user installed themselves (CloudStream extensions, Zangetsu sources, Aniyomi extensions),
     * so anything else simply resolves to nothing.
     */
    fun resolveStreams(token: String, isCasting: Boolean, callback: (ExtractorLink) -> Unit): Boolean {
        if (CsBridge.isCs3Slug(token)) return CsBridge.resolveStreams(token, isCasting, callback)
        if (com.lagradost.shiro.utils.zg.ZangetsuBridge.isZgSlug(token)) return com.lagradost.shiro.utils.zg.ZangetsuBridge.resolveStreams(token, callback)
        if (com.lagradost.shiro.utils.aniyomi.AniyomiBridge.isAySlug(token)) return com.lagradost.shiro.utils.aniyomi.AniyomiBridge.resolveStreams(token, callback)
        return false
    }

    // endregion
}
