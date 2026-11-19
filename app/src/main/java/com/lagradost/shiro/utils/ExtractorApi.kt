package com.lagradost.shiro.utils


data class ExtractorLink(
    val name: String,
    val url: String,
    val referer: String,
    val quality: Int,
    val isM3u8: Boolean = false,
    val isDash: Boolean = false,
    val headers: Map<String, String>? = null,
)

enum class Qualities(var value: Int) {
    Unknown(0),
    SD(-1), // 360p - 480p
    HD(1), // 720p
    FullHd(2), // 1080p
    UHD(3) // 4k
}

/**
 * "Preferred video quality" (Player settings): links at or below the cap come first (best
 * within the cap wins), then anything above it nearest-first as fallback. A cap of
 * [Qualities.UHD] means "no preference" and keeps the original highest-first order.
 */
fun preferredQualityCap(prefs: android.content.SharedPreferences): Int =
    prefs.getString("preferred_quality", "${Qualities.UHD.value}")?.toIntOrNull() ?: Qualities.UHD.value

fun Array<ExtractorLink>.sortForPreferredQuality(cap: Int): List<ExtractorLink> =
    if (cap >= Qualities.UHD.value) sortedBy { -it.quality }
    else sortedWith(compareBy({ it.quality > cap }, { if (it.quality <= cap) -it.quality else it.quality }))

/** Links at or above this quality auto-start the player (same as the old UHD/FHD rule when no cap is set). */
fun quickstartQuality(cap: Int): Int = minOf(cap, Qualities.FullHd.value)

private val packedRegex = Regex("""eval\(function\(p,a,c,k,e,.*\)\)""")
fun getPacked(string: String): String? {
    return packedRegex.find(string)?.value
}

fun getAndUnpack(string: String): String? {
    val packedText = getPacked(string)
    return packedText?.let { JsUnpacker(it).unpack() }
}

// Nekomo bundles no host extractors or streaming sources; everything playable comes from sources the
// user installs (CloudStream extensions, Zangetsu sources, Aniyomi extensions).
val APIS: Array<ExtractorApi> = arrayOf()

/*val linKLoadingTimeout: Double
    get() {
        return (settingsManager?.getInt("link_loading_timeout", 15) ?: 15).toDouble()
    }*/

fun httpsify(url: String): String {
    return if (url.startsWith("//")) "https:$url" else url
}

abstract class ExtractorApi {
    abstract val name: String
    abstract val mainUrl: String
    abstract val requiresReferer: Boolean

    abstract fun getUrl(url: String, referer: String? = null): List<ExtractorLink>?

    open fun getExtractorUrl(id: String): String {
        return id
    }
}