package com.lagradost.shiro.utils.extractors

import com.lagradost.shiro.utils.APIS
import com.lagradost.shiro.utils.ExtractorLink
import com.lagradost.shiro.utils.mvvm.logError
import com.lagradost.shiro.utils.pmap
import org.jsoup.Jsoup

class Vidstream(var providersActive: HashSet<String> = HashSet()) {
    val name: String = "Vidstream"
    private val mainUrl: String
        get() {
            return "https://streamani.net"
//            if (settingsManager?.getBoolean(
//                    "alternative_vidstream",
//                    false
//                ) == true
//            ) "https://streamani.net" else "https://gogo-stream.com"
        }

    private fun getExtractorUrl(id: String): String {
        return "$mainUrl/streaming.php?id=$id"
    }

    // https://gogo-stream.com/streaming.php?id=MTE3NDg5
    //   https://streamani.net/streaming.php?id=MTE3NDg5
    fun getUrl(id: String, isCasting: Boolean = false, callback: (ExtractorLink) -> Unit): Boolean {
        // New backend: episode sources are "<showRef>|<episode>|<sub|dub>" tokens
        if (id.contains("|") || com.lagradost.shiro.utils.cs3.CsBridge.isCs3Slug(id)) {
            return try {
                com.lagradost.shiro.utils.LiveApi.resolveStreams(id, isCasting, callback)
            } catch (e: Exception) {
                logError(e)
                false
            }
        }
        // Legacy fastani/vidstream ids are dead with the old backend
        return false
    }
}