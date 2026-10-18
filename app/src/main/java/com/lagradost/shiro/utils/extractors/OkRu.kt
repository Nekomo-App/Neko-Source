package com.lagradost.shiro.utils.extractors

import com.lagradost.shiro.utils.ExtractorApi
import com.lagradost.shiro.utils.ExtractorLink
import com.lagradost.shiro.utils.Qualities
import com.lagradost.shiro.utils.ShiroApi
import com.lagradost.shiro.utils.mvvm.logError

/** ok.ru / odnoklassniki embeds: the embed page carries direct mp4 (and sometimes HLS) urls. */
class OkRu : ExtractorApi() {
    override val name: String = "OkRu"
    override val mainUrl: String = "https://ok.ru"
    override val requiresReferer = false

    private val videoRegex = Regex(""""name":"(\w+)","url":"(https:[^"]+)"""")
    private val hlsRegex = Regex(""""hlsManifestUrl":"(https:[^"]+)"""")

    private fun String.clean() = replace("\\u0026", "&").replace("\\/", "/")

    override fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        if (!url.contains("ok.ru") && !url.contains("odnoklassniki")) return null
        return try {
            val html = khttp.get(
                url, headers = mapOf("User-Agent" to ShiroApi.USER_AGENT), timeout = 20.0
            ).text
            val text = html.replace("&quot;", "\"").replace("&amp;", "&")
            val links = mutableListOf<ExtractorLink>()
            hlsRegex.find(text)?.groupValues?.get(1)?.clean()?.let {
                links.add(ExtractorLink(name, it, "https://ok.ru/", Qualities.FullHd.value, true))
            }
            videoRegex.findAll(text).forEach { m ->
                val label = m.groupValues[1]
                val quality = when (label) {
                    "full", "quad", "ultra" -> Qualities.FullHd
                    "hd" -> Qualities.HD
                    else -> Qualities.SD
                }
                links.add(ExtractorLink("$name $label", m.groupValues[2].clean(), "https://ok.ru/", quality.value))
            }
            links.ifEmpty { null }
        } catch (e: Exception) {
            logError(e)
            null
        }
    }
}
