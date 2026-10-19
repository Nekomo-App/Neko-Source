package com.lagradost.shiro.ui.manga

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.shiro.utils.mihon.MihonBridge
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Page
import kotlin.concurrent.thread

/** Vertical-scroll manga reader: pages are fetched through the source's own HTTP client. */
class MangaReaderActivity : AppCompatActivity() {
    private var index = 0
    private var src: CatalogueSource? = null
    private var pages: List<Page> = emptyList()
    private lateinit var recycler: RecyclerView
    private lateinit var label: TextView
    private lateinit var progress: View
    private lateinit var message: TextView
    private var screenWidth = 1080

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        screenWidth = resources.displayMetrics.widthPixels
        index = intent.getIntExtra("index", 0)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        recycler = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@MangaReaderActivity) }
        label = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            setPadding(dp(12), dp(6), dp(12), dp(6))
            textSize = 13f
        }
        progress = android.widget.ProgressBar(this)
        message = TextView(this).apply { setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(dp(24), 0, dp(24), 0); visibility = View.GONE }
        root.addView(recycler, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(label, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))
        root.addView(progress, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        root.addView(message, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        setContentView(root)
        loadChapter()
    }

    private fun loadChapter() {
        val chapters = MangaSession.chapters
        val ch = chapters.getOrNull(index) ?: return finish()
        label.text = "${MangaSession.mangaTitle} · ${ch.name}"
        progress.visibility = View.VISIBLE
        message.visibility = View.GONE
        recycler.adapter = null
        thread {
            val source = src ?: try { MihonBridge.sourceById(this, MangaSession.sourceId) } catch (t: Throwable) { null }
            src = source
            val list = try {
                if (source == null) emptyList() else MihonBridge.pages(source, ch.url, ch.name)
            } catch (t: Throwable) {
                com.lagradost.shiro.utils.mvvm.logError(t)
                emptyList()
            }
            runOnUiThread {
                progress.visibility = View.GONE
                if (list.isEmpty()) {
                    message.text = "This chapter has no pages available.\nIt may be removed, locked, or require a login on the source's website."
                    message.visibility = View.VISIBLE
                    return@runOnUiThread
                }
                pages = list
                markRead(ch.url)
                recycler.adapter = PageAdapter()
            }
        }
    }

    private fun markRead(url: String) {
        val prefs = getSharedPreferences("manga_read", MODE_PRIVATE)
        prefs.edit().putStringSet("urls", prefs.getStringSet("urls", emptySet())!!.toMutableSet().apply { add(url) }).apply()
    }

    private fun go(delta: Int) {
        val next = index + delta
        if (next !in MangaSession.chapters.indices) {
            Toast.makeText(this, if (delta > 0) "No newer chapter" else "No earlier chapter", Toast.LENGTH_SHORT).show()
            return
        }
        // Sources list newest first, so "next" in reading order is the previous list entry
        index = next
        loadChapter()
    }

    private inner class PageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val TYPE_PAGE = 0
        private val TYPE_END = 1

        override fun getItemCount() = pages.size + 1
        override fun getItemViewType(position: Int) = if (position == pages.size) TYPE_END else TYPE_PAGE

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == TYPE_END) {
                val box = LinearLayout(this@MangaReaderActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(dp(12), dp(24), dp(12), dp(48))
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    addView(Button(this@MangaReaderActivity).apply { text = "Previous chapter"; setOnClickListener { go(+1) } })
                    addView(Button(this@MangaReaderActivity).apply { text = "Next chapter"; setOnClickListener { go(-1) } })
                }
                return object : RecyclerView.ViewHolder(box) {}
            }
            val iv = ImageView(this@MangaReaderActivity).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                minimumHeight = dp(300)
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            return object : RecyclerView.ViewHolder(iv) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (position == pages.size) return
            val iv = holder.itemView as ImageView
            val page = pages[position]
            val key = "${MangaSession.sourceId}:${MangaSession.chapters.getOrNull(index)?.url}:$position"
            iv.tag = key
            iv.setImageDrawable(null)
            MangaSession.getBitmap(key)?.let { iv.setImageBitmap(it); return }
            val source = src ?: return
            MangaSession.run {
                val bytes = MihonBridge.imageBytes(source, page)
                val bmp = bytes?.let { MangaSession.decode(it, screenWidth) }
                if (bmp != null) {
                    MangaSession.putBitmap(key, bmp)
                    iv.post {
                        if (iv.tag == key) {
                            // very tall webtoon strips exceed the GPU texture limit; draw those in software
                            if (bmp.height > 4096) iv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                            else iv.setLayerType(View.LAYER_TYPE_NONE, null)
                            iv.setImageBitmap(bmp)
                        }
                    }
                } else {
                    iv.post { if (iv.tag == key) iv.setBackgroundColor(0x33FF0000) }
                }
            }
        }
    }
}
