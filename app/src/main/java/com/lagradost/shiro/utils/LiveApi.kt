package com.lagradost.shiro.utils

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.lagradost.shiro.utils.cs3.CsBridge
import com.lagradost.shiro.utils.mvvm.logError
import java.net.URI
import kotlin.random.Random as KotlinRandom

/**
 * Replacement backend for the dead fastani.net API.
 *
 * Catalog/metadata/search  -> AniList GraphQL (graphql.anilist.co)
 * Episode titles/thumbs    -> api.ani.zip
 * Streams                  -> mkissa (allanime) frontend via WebView + host extractors
 *
 * Slug scheme: "al<anilistId>" when an AniList id is known, "aa<allanimeShowId>"
 * otherwise. Appending "-dub" selects the dub translation, matching the old
 * codebase's dubbify()/removeSuffix("-dub") convention.
 *
 * Episode sources are opaque tokens "<showRef>|<episode>|<sub|dub>" carried in
 * the legacy sources JSON [{"slug":"gogostream","source":"<token>"}] and
 * resolved lazily by Vidstream/LiveApi.resolveStreams.
 */
object LiveApi {
    private const val ANILIST_GQL = "https://graphql.anilist.co"
    private const val ALLANIME_API = "https://api.mkissa.net/api"
    private const val ALLANIME_ORIGIN = "https://api.mkissa.net"
    private const val ALLANIME_CLOCK = "https://allanime.day"
    private const val ANIZIP_API = "https://api.ani.zip"

    private val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

    private val allAnimeHeaders
        get() = mapOf(
            "Referer" to "https://mkissa.to",
            "User-Agent" to ShiroApi.USER_AGENT
        )

    private val defaultHeaders get() = mapOf("User-Agent" to ShiroApi.USER_AGENT)

    // region ---------- HTTP helpers ----------

    private fun gql(url: String, query: String, variables: Map<String, Any?>): JsonNode? {
        return try {
            val body = mapper.writeValueAsString(mapOf("query" to query, "variables" to variables))
            val headers =
                if (url.contains("allanime")) allAnimeHeaders else defaultHeaders
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

    private fun allanime(query: String, variables: Map<String, Any?>): JsonNode? =
        gql(ALLANIME_API, query, variables)?.path("data")

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

    private const val AA_SEARCH_QUERY = """
        query(${"$"}search: SearchInput) {
            shows(search: ${"$"}search) {
                edges { _id name englishName thumbnail availableEpisodesDetail }
            }
        }
    """

    private const val AA_SHOW_QUERY = """
        query(${"$"}id: String!) {
            show(_id: ${"$"}id) {
                _id name englishName nativeName description thumbnail banner
                availableEpisodesDetail
            }
        }
    """

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

    private fun allAnimeSearch(query: String): List<JsonNode> {
        val data = allanime(
            AA_SEARCH_QUERY,
            mapOf("search" to mapOf("query" to query, "isManga" to false))
        ) ?: return emptyList()
        return data.path("shows").path("edges").toList()
    }

    /** Extract an AniList id from an anilistcdn cover url (bx<ID>-...). */
    private fun anilistIdFromThumb(thumb: String): Int? =
        Regex("""anilistcdn/media/(?:anime|manga)/cover/[a-z]+/bx(\d+)-""")
            .find(thumb)?.groupValues?.get(1)?.toIntOrNull()

    fun search(query: String, genres: List<String>?): List<ShiroApi.Companion.Data>? {
        if (!genres.isNullOrEmpty()) {
            val data = anilist(SEARCH_QUERY, mapOf("q" to query, "genres" to genres))
                ?: return null
            return data.path("Page").path("media").map { mediaToData(it) }
        }

        val edges = allAnimeSearch(query)
        val out = mutableListOf<ShiroApi.Companion.Data>()
        edges.forEach { edge ->
            val thumb = edge.txt("thumbnail")
            val alId = anilistIdFromThumb(thumb)
            val aaId = edge.txt("_id")
            val subSlug = if (alId != null) "al$alId" else "aa$aaId"
            val eps = edge.path("availableEpisodesDetail")
            val hasSub = eps.path("sub").size() > 0
            val hasDub = eps.path("dub").size() > 0

            fun makeData(slug: String, dubbed: Boolean): ShiroApi.Companion.Data {
                val baseName = edge.txt("englishName").ifEmpty { edge.txt("name") }
                return ShiroApi.Companion.Data(
                    id = aaId,
                    slug = slug,
                    title = if (dubbed) "$baseName (Dub)" else baseName,
                    title_english = edge.txt("englishName").ifEmpty { null },
                    native_title = "",
                    poster = thumb,
                    banner = "",
                    ids = mapper.writeValueAsString(
                        mapOf("mal" to null, "anilist" to alId?.toString())
                    ),
                    type = "anime",
                    format = "",
                    episodes = eps.path(if (dubbed) "dub" else "sub").size().toString(),
                    episode_duration = "",
                    synopsis = "",
                    language = if (dubbed) "dub" else "sub",
                    synonyms = "",
                    season = "",
                    release_year = null,
                    score = "",
                    rating = "",
                    studios = "",
                    genres = "",
                    aired = "",
                    status = "",
                    trailer = "",
                    total_views = "0",
                    created_at = "",
                    updated_at = ""
                )
            }
            if (hasSub || !hasDub) out.add(makeData(subSlug, false))
            if (hasDub) out.add(makeData("$subSlug-dub", true))
        }
        return out
    }

    // endregion

    // region ---------- anime page ----------

    private fun anilistMediaById(id: Int): JsonNode? =
        anilist(MEDIA_BY_ID_QUERY, mapOf("id" to id))?.path("Media")

    private fun anilistMediaByMal(id: Int): JsonNode? =
        anilist(MEDIA_BY_MAL_QUERY, mapOf("id" to id))?.path("Media")

    private fun allAnimeShow(id: String): JsonNode? =
        allanime(AA_SHOW_QUERY, mapOf("id" to id))?.path("show")

    /** Find the best matching AllAnime show for a set of candidate titles. */
    private fun findAllAnimeId(titles: List<String>, episodeCount: Int = 0): String? {
        for (t in titles.filter { it.isNotBlank() }) {
            val edges = allAnimeSearch(t)
            if (edges.isEmpty()) continue
            val norm = normTitle(t)
            val exact = edges.firstOrNull {
                normTitle(it.txt("name")) == norm || normTitle(it.txt("englishName")) == norm
            }
            val best = exact ?: edges.firstOrNull { e ->
                // Prefer the candidate whose episode count matches when titles are fuzzy
                episodeCount <= 0 ||
                        e.path("availableEpisodesDetail").path("sub").size() >= episodeCount
            } ?: edges.first()
            return best.txt("_id").ifEmpty { null }
        }
        return null
    }

    private fun episodeSortKey(ep: String): Float = ep.toFloatOrNull() ?: Float.MAX_VALUE

    private fun buildEpisodes(
        showRef: String,
        epStrings: List<String>,
        translationType: String,
        zipMeta: Map<String, JsonNode>
    ): List<ShiroApi.Companion.AnimePageNewEpisodes> {
        return epStrings.distinct().sortedBy { episodeSortKey(it) }.map { ep ->
            val meta = zipMeta[ep]
            ShiroApi.Companion.AnimePageNewEpisodes(
                id = "$showRef-$ep",
                slug = "$showRef-$ep",
                title = meta?.txt("title", "en")?.ifEmpty { null },
                episode = ep,
                image = meta?.txt("image")?.ifEmpty { null },
                insight = null,
                sources = "[{\"slug\":\"gogostream\",\"source\":\"$showRef|$ep|$translationType\"}]",
                ext = null,
                views = "0",
                created_at = "",
                updated_at = ""
            )
        }
    }

    /** ani.zip episode metadata keyed by episode string, null on failure. */
    private fun aniZipEpisodes(anilistId: Int): Map<String, JsonNode>? {
        return try {
            val res = khttp.get("$ANIZIP_API/episodes?anilist_id=$anilistId", headers = defaultHeaders)
            if (res.statusCode != 200) return null
            mapper.readTree(res.text).path("episodes").fields().asSequence()
                .map { it.key to it.value }.toMap()
        } catch (e: Exception) {
            null
        }
    }

    private fun JsonNode.epList(tt: String): List<String> =
        path("availableEpisodesDetail").path(tt).mapNotNull { it.asText(null) }

    fun getAnimePage(slug: String): ShiroApi.Companion.AnimePageNewRoot? {
        val dubbed = slug.endsWith("-dub")
        val bare = slug.removeSuffix("-dub")
        val tt = if (dubbed) "dub" else "sub"

        // CloudStream extension slugs are self-contained "cs3-hexApi-hexUrl" tokens
        if (CsBridge.isCs3Slug(bare)) return CsBridge.getAnimePage(bare)

        try {
            when {
                bare.startsWith("al") -> {
                    val alId = bare.drop(2).toIntOrNull() ?: return null
                    val media = anilistMediaById(alId) ?: return null
                    val titles = listOf(
                        media.txt("title", "romaji"),
                        media.txt("title", "english"),
                        media.txt("title", "native")
                    )
                    val epCount = media.path("episodes").asInt(0)
                    val aaId = findAllAnimeId(titles, epCount)
                    val zip = aniZipEpisodes(alId) ?: emptyMap()

                    val show = aaId?.let { allAnimeShow(it) }
                    val epStrings = show?.epList(tt)?.ifEmpty { show.epList("sub") }
                        ?: zip.keys.toList()
                        ?: (1..epCount).takeIf { epCount > 0 }?.map { it.toString() }
                        ?: emptyList()

                    val showRef = aaId?.let { "aa$it" } ?: "al$alId"
                    val episodes = buildEpisodes(showRef, epStrings, tt, zip)

                    val d = mediaToData(media, slugOverride = slug)
                    return ShiroApi.Companion.AnimePageNewRoot(
                        "success", "",
                        ShiroApi.Companion.AnimePageNewData(
                            anime = dataToAnimePage(d, slug),
                            episodes = episodes
                        )
                    )
                }
                bare.startsWith("aa") -> {
                    val aaId = bare.drop(2)
                    val show = allAnimeShow(aaId) ?: return null
                    val alId = anilistIdFromThumb(show.txt("thumbnail"))
                    val media = alId?.let { anilistMediaById(it) }
                        ?: anilistSearchMedia(listOf(show.txt("name"), show.txt("englishName")))
                    val zip = alId?.let { aniZipEpisodes(it) } ?: emptyMap()

                    val epStrings = show.epList(tt).ifEmpty { show.epList("sub") }
                    val episodes = buildEpisodes("aa$aaId", epStrings, tt, zip)

                    val d = media?.let { mediaToData(it, slugOverride = slug) }
                        ?: ShiroApi.Companion.Data(
                            id = aaId, slug = slug,
                            title = show.txt("englishName").ifEmpty { show.txt("name") }
                                .let { if (dubbed) "$it (Dub)" else it },
                            title_english = show.txt("englishName").ifEmpty { null },
                            native_title = show.txt("nativeName"),
                            poster = show.txt("thumbnail"),
                            banner = show.txt("banner"),
                            ids = mapper.writeValueAsString(
                                mapOf("mal" to null, "anilist" to alId?.toString())
                            ),
                            type = "anime", format = "",
                            episodes = epStrings.size.toString(),
                            episode_duration = "",
                            synopsis = show.txt("description"),
                            language = tt, synonyms = "", season = "",
                            release_year = null, score = "", rating = "",
                            studios = "", genres = "", aired = "", status = "",
                            trailer = "", total_views = "0",
                            created_at = "", updated_at = ""
                        )
                    return ShiroApi.Companion.AnimePageNewRoot(
                        "success", "",
                        ShiroApi.Companion.AnimePageNewData(
                            anime = dataToAnimePage(d, slug),
                            episodes = episodes
                        )
                    )
                }
                else -> {
                    // Legacy slug: last-resort title search
                    val title = bare.replace('-', ' ')
                    val aaId = findAllAnimeId(listOf(title)) ?: return null
                    return getAnimePage("aa$aaId" + if (dubbed) "-dub" else "")
                }
            }
        } catch (e: Exception) {
            logError(e)
            return null
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
     * Tokens: "<aaShowId>|<episode>|<sub|dub>" or "al<anilistId>|<episode>|<type>".
     * Resolves the AllAnime episode source list and emits ExtractorLinks.
     */
    fun resolveStreams(token: String, isCasting: Boolean, callback: (ExtractorLink) -> Unit): Boolean {
        // CloudStream extension tokens route to the provider's loadLinks
        if (CsBridge.isCs3Slug(token)) return CsBridge.resolveStreams(token, isCasting, callback)
        try {
            val parts = token.split("|")
            if (parts.size != 3) return false
            val (ref, ep, tt) = parts

            val showId = when {
                ref.startsWith("aa") -> ref.drop(2)
                ref.startsWith("al") -> {
                    val media = ref.drop(2).toIntOrNull()?.let { anilistMediaById(it) }
                    val titles = listOf(
                        media.txt("title", "romaji"),
                        media.txt("title", "english"),
                        media.txt("title", "native")
                    )
                    findAllAnimeId(titles, media?.path("episodes")?.asInt(0) ?: 0)
                }
                else -> ref
            } ?: return false

            // Plain GraphQL is gated behind request crypto (AA_CRYPTO_MISSING);
            // the episode node is fetched through the site's own JS in Mkissa.
            val episode = Mkissa.fetchEpisode(showId, ep, tt) ?: return false

            val urls = episode.path("sourceUrls")
            val list: List<JsonNode> = when {
                urls.isArray -> urls.toList()
                urls.isTextual -> try {
                    mapper.readTree(urls.asText()).let {
                        if (it.isArray) it.toList() else listOf(it)
                    }
                } catch (e: Exception) {
                    emptyList()
                }
                else -> emptyList()
            }

            var emitted = false
            list.forEach { node ->
                val raw = node.txt("sourceUrl").ifEmpty { node.txt("url") }
                if (raw.isBlank()) return@forEach
                val name = node.txt("sourceName").ifEmpty { "AllAnime" }
                val url = decodeSourceUrl(raw) ?: return@forEach

                when {
                    url.startsWith("/") -> {
                        // AllAnime "clock" api -> direct links; try the legacy
                        // allanime.day host first, then the mkissa api host.
                        emitted = emitClockLinks(ALLANIME_CLOCK + url, name, callback) ||
                                emitClockLinks(ALLANIME_ORIGIN + url, name, callback) || emitted
                    }
                    url.endsWith(".mp4") || url.contains(".m3u8") -> {
                        callback(
                            ExtractorLink(
                                name, url, "https://mkissa.to",
                                qualityOf(url), url.contains(".m3u8")
                            )
                        )
                        emitted = true
                    }
                    else -> {
                        emitted = runExtractors(url, name, isCasting, callback) || emitted
                    }
                }
            }
            return emitted
        } catch (e: Exception) {
            logError(e)
            return false
        }
    }

    /**
     * AllAnime obfuscated urls are prefixed with "--" followed by hex pairs,
     * each pair substituted through the site's static character table.
     */
    private val sourceSubs = mapOf(
        "79" to 'A', "7a" to 'B', "7b" to 'C', "7c" to 'D', "7d" to 'E',
        "7e" to 'F', "7f" to 'G', "70" to 'H', "71" to 'I', "72" to 'J',
        "73" to 'K', "74" to 'L', "75" to 'M', "76" to 'N', "77" to 'O',
        "68" to 'P', "69" to 'Q', "6a" to 'R', "6b" to 'S', "6c" to 'T',
        "6d" to 'U', "6e" to 'V', "6f" to 'W', "60" to 'X', "61" to 'Y',
        "62" to 'Z', "59" to 'a', "5a" to 'b', "5b" to 'c', "5c" to 'd',
        "5d" to 'e', "5e" to 'f', "5f" to 'g', "50" to 'h', "51" to 'i',
        "52" to 'j', "53" to 'k', "54" to 'l', "55" to 'm', "56" to 'n',
        "57" to 'o', "48" to 'p', "49" to 'q', "4a" to 'r', "4b" to 's',
        "4c" to 't', "4d" to 'u', "4e" to 'v', "4f" to 'w', "40" to 'x',
        "41" to 'y', "42" to 'z', "08" to '0', "09" to '1', "0a" to '2',
        "0b" to '3', "0c" to '4', "0d" to '5', "0e" to '6', "0f" to '7',
        "00" to '8', "01" to '9', "15" to '-', "16" to '.', "67" to '_',
        "46" to '~', "02" to ':', "17" to '/', "07" to '?', "1b" to '#',
        "63" to '[', "65" to ']', "78" to '@', "19" to '!', "1c" to '$',
        "1e" to '&', "10" to '(', "11" to ')', "12" to '*', "13" to '+',
        "14" to ',', "03" to ';', "05" to '=', "1d" to '%'
    )

    private fun decodeSourceUrl(raw: String): String? {
        if (!raw.startsWith("--")) return if (raw.startsWith("//")) "https:$raw" else raw
        val hex = raw.drop(2)
        return try {
            val sb = StringBuilder()
            var i = 0
            while (i + 2 <= hex.length) {
                val pair = hex.substring(i, i + 2).lowercase()
                sb.append(sourceSubs[pair] ?: pair.toInt(16).toChar())
                i += 2
            }
            var decoded = sb.toString()
            decoded = decoded
                .replace("/clock?", "/clock.json?")
                .let { if (it.endsWith("/clock")) "$it.json" else it }
            if (decoded.startsWith("/") || decoded.startsWith("http")) decoded else null
        } catch (e: Exception) {
            null
        }
    }

    private fun emitClockLinks(
        clockUrl: String,
        name: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val res = khttp.get(clockUrl, headers = allAnimeHeaders)
            if (res.statusCode != 200) return false
            val links = mapper.readTree(res.text).path("links")
            var emitted = false
            links.forEach { link ->
                val l = link.txt("link").ifEmpty { link.txt("src") }
                if (l.isBlank()) return@forEach
                val resolution = link.txt("resolutionStr").ifEmpty { link.txt("resolution") }
                val isHls = link.path("hls").asBoolean(false) || l.contains(".m3u8")
                callback(
                    ExtractorLink(
                        if (resolution.isBlank()) name else "$name $resolution",
                        httpsify(l), "https://mkissa.to",
                        qualityOf(resolution.ifEmpty { l }), isHls
                    )
                )
                emitted = true
            }
            emitted
        } catch (e: Exception) {
            logError(e)
            false
        }
    }

    private fun qualityOf(s: String): Int = when {
        s.contains("1080") -> Qualities.FullHd.value
        s.contains("720") -> Qualities.HD.value
        s.contains("480") || s.contains("360") -> Qualities.SD.value
        else -> Qualities.Unknown.value
    }

    private val hostExtractorHints = mapOf(
        "streamtape" to "StreamTape",
        "mp4upload" to "Mp4Upload",
        "mixdrop" to "MixDrop",
        "streamsb" to "StreamSB",
        "sbplay" to "StreamSB",
        "ssbstream" to "StreamSB",
        "xstreamcdn" to "XStreamCdn",
        "fembed" to "XStreamCdn",
        "dood" to "XStreamCdn",
        "voe" to "XStreamCdn",
    )

    private fun runExtractors(
        url: String,
        name: String,
        isCasting: Boolean,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var emitted = false
        val host = try {
            URI(url).host?.lowercase() ?: ""
        } catch (e: Exception) {
            ""
        }

        val hinted = hostExtractorHints.entries
            .filter { host.contains(it.key) }
            .mapNotNull { h -> APIS.firstOrNull { it.name == h.value } }

        val candidates = (hinted + APIS.toList()).distinct()
        candidates.forEach { api ->
            if (api.requiresReferer && isCasting) return@forEach
            try {
                api.getUrl(url, url)?.forEach {
                    callback(it)
                    emitted = true
                }
            } catch (e: Exception) {
                // try next extractor
            }
        }
        if (!emitted) {
            // Last resort: hand the embed url itself over; the player may still fail,
            // but a webview/direct-capable link is better than nothing.
            callback(
                ExtractorLink(
                    name, url, "https://mkissa.to", Qualities.Unknown.value,
                    url.contains(".m3u8")
                )
            )
        }
        return true
    }

    // endregion
}
