package com.lagradost.shiro.utils

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.json.JsonMapper
import com.lagradost.shiro.AcraApplication
import com.lagradost.shiro.utils.mvvm.logError
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Episode stream resolution through the live allanime/mkissa frontend.
 *
 * The episode GraphQL endpoint requires request crypto (x-aa-boot bootstrap,
 * aaReq payload signing) that is derived from browser-only state and rotates
 * with every site build. Rather than reimplementing it, this loads the real
 * landing page markup into a hidden WebView (real DOM/storage/UA/Chromium
 * networking), evaluates the site's own crypto chunk in that context, and
 * calls the site's internal query runner — identified by the stable
 * destructured parameter name `queryOpts`, which survives minification.
 *
 * fetchEpisode() blocks the CALLING thread (never call it on the main
 * thread; the existing resolveStreams path already runs on a worker).
 */
object Mkissa {
    private const val ORIGIN = "https://mkissa.to"
    private const val CDN = "https://cdn.mkissa.net/all/mk"
    private const val BRIDGE = "NekomoBridge"
    private const val REQ_TIMEOUT_MS = 75_000L

    // Persisted-query hash observed for the episode document. Paired with the
    // site's own doc when available; the driver falls back to a raw query.
    private const val EPISODE_HASH =
        "670bbf38d0868f446e2346c1e956ca2c40c416e733ca248fd54e04f1c8b99145"

    private val EPISODE_DOC = """
        query (${"$"}showId: String!, ${"$"}translationType: VaildTranslationTypeEnumType!, ${"$"}episodeString: String!) {
          episode(showId: ${"$"}showId, episodeString: ${"$"}episodeString, translationType: ${"$"}translationType) {
            episodeString uploadDate sourceUrls thumbnail notes
            show { _id name thumbnail englishName nativeName }
          }
        }
    """.trimIndent()

    private val main = Handler(Looper.getMainLooper())
    private val mapper = JsonMapper.builder().build()
    private val reqLock = Any()

    private val headers
        get() = mapOf(
            "User-Agent" to ShiroApi.USER_AGENT,
            "Referer" to "$ORIGIN/"
        )

    // ---------- asset discovery (caller thread) ----------

    @Volatile
    private var injectionHtml: String? = null

    private fun httpText(url: String): String? = try {
        khttp.get(url, headers = headers, timeout = 30.0)
            .takeIf { it.statusCode == 200 }?.text
    } catch (e: Exception) {
        null
    }

    private fun findCryptoChunk(landing: String): String? {
        val appFile = Regex("""_app/immutable/entry/app\.[\w.-]+\.js""")
            .find(landing)?.value ?: return null
        val app = httpText("$CDN/$appFile") ?: return null
        val names = LinkedHashSet<String>()
        Regex("""["'](?:[.]{0,2}/)?chunks/([\w.-]+\.js)["']""").findAll(app)
            .forEach { names.add(it.groupValues[1]) }
        for (n in names) {
            val js = httpText("$CDN/_app/immutable/chunks/$n") ?: continue
            if (js.contains("x-aa-boot")) return js
        }
        return null
    }

    private fun jsStr(s: String): String = mapper.writeValueAsString(s)

    /**
     * Converts the site's ESM vendor chunk into a classic script: import
     * bindings become `__mk()` proxies, the export block is replaced by an
     * explicit capture object, and the query-runner/episode-doc functions are
     * fingerprinted by parameter structure (names rotate per build).
     */
    private fun buildChunkScript(chunk: String): String {
        val importNames = linkedSetOf<String>()
        val importRe = Regex(
            """import\s*(?:\{([^}]*)\}|([\w$]+)\s*,\s*\{([^}]*)\}|([\w$]+)|\*\s*as\s+([\w$]+))\s*from\s*["'][^"']*["']\s*;?|import\s*["'][^"']*["']\s*;?"""
        )
        for (m in importRe.findAll(chunk)) {
            for (grp in listOf(m.groupValues[1], m.groupValues[3])) {
                if (grp.isBlank()) continue
                grp.split(",").forEach { part ->
                    val seg = part.trim()
                    val asM = Regex("""\bas\s+([\w$]+)$""").find(seg)
                    if (asM != null) importNames.add(asM.groupValues[1])
                    else if (Regex("""^[\w$]+$""").matches(seg)) importNames.add(seg)
                }
            }
            for (gi in listOf(2, 4, 5)) {
                val v = m.groupValues[gi]
                if (v.isNotEmpty()) importNames.add(v)
            }
        }

        val exports = linkedMapOf<String, String>()
        Regex("""export\s*\{([^}]*)\}\s*;?""").findAll(chunk).forEach { m ->
            m.groupValues[1].split(",").forEach { part ->
                val seg = part.trim()
                if (seg.isEmpty()) return@forEach
                val asM = Regex("""^([\w$]+)\s+as\s+([\w$]+)$""").find(seg)
                if (asM != null) exports[asM.groupValues[2]] = asM.groupValues[1]
                else if (Regex("""^[\w$]+$""").matches(seg)) exports[seg] = seg
            }
        }
        Regex("""export\s+(?:async\s+)?(?:function|const|let|var|class)\s+([\w$]+)""")
            .findAll(chunk).forEach { exports.putIfAbsent(it.groupValues[1], it.groupValues[1]) }

        var src = chunk
            .replace(
                Regex("""import\s*(?:\{[^}]*\}|[\w$]+\s*,\s*\{[^}]*\}|[\w$]+|\*\s*as\s+[\w$]+)\s*from\s*["'][^"']*["']\s*;?"""),
                ""
            )
            .replace(Regex("""import\s*["'][^"']*["']\s*;?"""), "")
            .replace(Regex("""export\s*\{[^}]*\}\s*;?"""), "")
            .replace(
                Regex("""export\s+(?=async\s|function\s|const\s|let\s|var\s|class\s)"""),
                ""
            )
            .replace(Regex("""import\s*\.\s*meta"""), """({url:"x",env:{}})""")

        val hasDefault = Regex("""export\s+default\s""").containsMatchIn(src)
        if (hasDefault) {
            src = src.replace(Regex("""export\s+default\s+"""), "const __expDefault=")
        }

        val extras = StringBuilder()
        if (hasDefault) extras.append("try{__X['default']=__expDefault;}catch(e){}")
        val runName = listOf(
            Regex("""async\s+function\s+([\w$]+)\s*\(\s*\{queryOpts:"""),
            Regex("""function\s+([\w$]+)\s*\(\s*\{queryOpts:"""),
            Regex("""([\w$]+)\s*=\s*async\s+function\s*\(\s*\{queryOpts:"""),
            Regex("""([\w$]+)\s*=\s*async\s*\(\s*\{queryOpts:"""),
            Regex("""([\w$]+)\s*=\s*\(\s*\{queryOpts:"""),
        ).firstNotNullOfOrNull { it.find(src)?.groupValues?.get(1) }
        if (runName != null) extras.append("try{__X.__run=$runName;}catch(e){}")

        val epM = Regex(
            """(?:async\s+)?function\s+([\w$]+)\s*\(\s*[\w$]+\s*,\s*[\w$]+\s*\)\s*\{\s*const\s*\{\s*showId:"""
        ).find(src)
        if (epM != null) {
            extras.append("try{__X.__ep=${epM.groupValues[1]};}catch(e){}")
            val epBody = src.substring(
                epM.range.first,
                minOf(epM.range.first + 4000, src.length)
            )
            Regex("""query\s*:\s*([\w$]+)\s*\(\s*\)""").find(epBody)?.let {
                extras.append("try{__X.__doc=${it.groupValues[1]};}catch(e){}")
            }
        }

        val reserved = setOf(
            "default", "let", "const", "var", "function", "class", "return",
            "if", "else", "for", "while", "do", "switch", "case", "break",
            "continue", "new", "delete", "typeof", "instanceof", "in", "of",
            "try", "catch", "finally", "throw", "this", "super", "extends",
            "import", "export", "yield", "async", "await", "static", "get",
            "set", "null", "true", "false", "undefined", "void", "with",
            "enum", "implements", "interface", "package", "private",
            "protected", "public", "debugger", "arguments", "eval"
        )
        val identRe = Regex("""^[A-Za-z_$][\w$]*$""")
        val importDecls = importNames
            .filter { identRe.matches(it) && it !in reserved }
            .joinToString("") { "let $it=__mk();" }
        val exportDecls = exports.entries.joinToString("") { (ext, local) ->
            "try{__X[${jsStr(ext)}]=$local;}catch(e){}"
        }

        return """
            ;(function(){try{
            const __mk=()=>new Proxy(function(){return __mk()},{get(t,p){if(p===Symbol.iterator)return function*(){};if(p==='then')return undefined;return __mk()},apply(){return __mk()},construct(){return __mk()}});
            const process=undefined,require=undefined,module=undefined,Deno=undefined,Buffer=undefined,global=undefined;
            $importDecls
            $src
            const __X={};
            $exportDecls
            $extras
            window.__NEKO=__X;
            }catch(e){window.__NEKO_ERR=String(e&&e.stack||e)}})();
        """.trimIndent().replace("</script", "<\\/script")
    }

    private fun buildInjectionHtml(): String? {
        val landing = httpText("$ORIGIN/") ?: return null
        val chunk = findCryptoChunk(landing) ?: return null
        val script = buildChunkScript(chunk)
        // Insert by position: the script is full of '$' and '\', which Regex.replace would treat as
        // group references / escapes ("Illegal group reference").
        val tag = "<script>$script</script>"
        val bodyEnd = landing.lastIndexOf("</body>", ignoreCase = true)
        return if (bodyEnd >= 0) landing.substring(0, bodyEnd) + tag + landing.substring(bodyEnd)
        else landing + tag
    }

    // ---------- webview driver ----------

    private fun driverJs(showId: String, ep: String, tt: String): String {
        val vars = mapper.writeValueAsString(
            mapOf("showId" to showId, "episodeString" to ep, "translationType" to tt)
        )
        return """
        (function(){
          var DONE=false;
          function rep(o){if(DONE)return;DONE=true;try{NekomoBridge.report(JSON.stringify(o))}catch(e){try{NekomoBridge.report('{"__error":"rep"}')}catch(_){}}}
          var t0=Date.now();
          (function go(){
            var M=window.__NEKO;
            if(!M||typeof M.__run!=='function'){
              if(window.__NEKO_ERR){rep({__error:'mod:'+String(window.__NEKO_ERR).slice(0,400)});return}
              if(Date.now()-t0>25000){rep({__error:'module-timeout'});return}
              return setTimeout(go,200);
            }
            // let the real app boot finish writing any crypto state first
            if(!M.__arm){if(Date.now()-t0<3500){return setTimeout(go,300)}M.__arm=1}
            var args=$vars;
            var doc=null;try{doc=M.__doc&&M.__doc()}catch(e){}
            var tries=[];
            if(doc){
              tries.push({queryOpts:{query:doc,variables:args},queryHash:${jsStr(EPISODE_HASH)},useAccount:true});
              tries.push({queryOpts:{query:doc,variables:args},useAccount:true});
            }
            tries.push({queryOpts:{query:${jsStr(EPISODE_DOC)},variables:args},useAccount:true});
            tries.push({queryOpts:{query:${jsStr(EPISODE_DOC)},variables:args}});
            var i=0;
            (function next(){
              if(i>=tries.length){rep({__error:'tries-exhausted'});return}
              var spec=tries[i++];
              var to=setTimeout(function(){next()},30000);
              var settled=false;
              try{
                Promise.resolve(M.__run(spec)).then(function(r){
                  if(settled)return;settled=true;clearTimeout(to);
                  var res=r&&r.result?r.result:r;
                  var data=res&&res.data?res.data:null;
                  var epn=data&&data.episode!==undefined?data.episode:(res&&res.episode!==undefined?res.episode:null);
                  if(epn&&epn.sourceUrls){rep({episode:epn});return}
                  if(r&&r.error){next();return}
                  rep({episode:epn,keys:res&&typeof res==='object'?Object.keys(res):null});
                },function(){if(settled)return;settled=true;clearTimeout(to);next()});
              }catch(e){clearTimeout(to);next()}
            })();
          })();
        })();
        """.trimIndent()
    }

    @Volatile
    private var webView: WebView? = null

    @Volatile
    private var pageReady = false

    private val pendingResult = AtomicReference<((String) -> Unit)?>(null)
    private val pendingDriver = AtomicReference<String?>(null)

    class Bridge {
        @JavascriptInterface
        fun report(json: String) {
            Mkissa.pendingResult.get()?.invoke(json)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun runOnPage(ctx: android.content.Context, html: String, driver: String) {
        var v = webView
        if (v == null) {
            v = WebView(ctx.applicationContext)
            v.settings.javaScriptEnabled = true
            v.settings.domStorageEnabled = true
            v.settings.userAgentString = ShiroApi.USER_AGENT
            v.addJavascriptInterface(Bridge(), BRIDGE)
            v.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    pageReady = true
                    pendingDriver.getAndSet(null)?.let { d ->
                        view.evaluateJavascript(d, null)
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    // Only fail on main-frame errors; subresource failures
                    // (app chunks, images) don't block our injected module.
                    if (failingUrl == null || failingUrl.startsWith(ORIGIN)) {
                        pendingResult.get()?.invoke("{\"__error\":\"page-load:$errorCode\"}")
                    }
                }
            }
            webView = v
            pageReady = false
            v.loadDataWithBaseURL("$ORIGIN/", html, "text/html", "utf-8", null)
        }
        if (pageReady) v.evaluateJavascript(driver, null)
        else pendingDriver.set(driver)
    }

    private fun resetWebView() {
        main.post {
            try {
                webView?.destroy()
            } catch (e: Exception) {
            }
            webView = null
            pageReady = false
        }
    }

    /**
     * Fetches decrypted episode data (the GraphQL `episode` node) through the
     * site's own query machinery. Blocks the caller; call off the main thread.
     */
    fun fetchEpisode(showId: String, episode: String, translationType: String): JsonNode? {
        synchronized(reqLock) {
            val ctx = try {
                AcraApplication.context
            } catch (e: Throwable) {
                null
            } ?: return null

            val html = injectionHtml ?: try {
                buildInjectionHtml()?.also { injectionHtml = it }
            } catch (e: Throwable) {
                logError(Exception(e))
                null
            } ?: return null

            val latch = CountDownLatch(1)
            val out = AtomicReference<String?>(null)
            pendingResult.set { json ->
                pendingResult.set(null)
                out.set(json)
                latch.countDown()
            }
            val driver = driverJs(showId, episode, translationType)

            try {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    // Defensive: never eval or block on the UI thread
                    pendingResult.set(null)
                    return null
                }
                main.post { runOnPage(ctx, html, driver) }
                if (!latch.await(REQ_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    pendingResult.set(null)
                    resetWebView()
                    return null
                }
            } catch (e: Throwable) {
                pendingResult.set(null)
                logError(Exception(e))
                return null
            }

            val json = out.get() ?: return null
            return try {
                val tree = mapper.readTree(json)
                val err = tree.path("__error").asText("")
                if (err.isNotEmpty()) {
                    if (err.startsWith("mod:") || err.contains("module") ||
                        err.contains("page-load")
                    ) {
                        injectionHtml = null
                        resetWebView()
                    }
                    null
                } else {
                    tree.path("episode").takeIf { !it.isNull }
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
