package com.lagradost.shiro.ui.manga

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.jaredrummler.cyanea.app.CyaneaAppCompatActivity
import com.lagradost.shiro.utils.mihon.MihonBridge
import kotlin.concurrent.thread

/** Manga details and chapter list; tapping a chapter opens the reader. */
class MangaDetailActivity : CyaneaAppCompatActivity() {
    private var details: MihonBridge.MangaDetails? = null
    private lateinit var list: ListView
    private lateinit var status: TextView
    private lateinit var header: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var meta: TextView
    private lateinit var desc: TextView
    private lateinit var cover: ImageView
    private val read: MutableSet<String> by lazy {
        getSharedPreferences("manga_read", MODE_PRIVATE).getStringSet("urls", emptySet())!!.toMutableSet()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sourceId = intent.getLongExtra("sourceId", 0)
        val url = intent.getStringExtra("url") ?: return finish()
        val fallbackTitle = intent.getStringExtra("title") ?: ""
        title = fallbackTitle
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        titleView = TextView(this).apply { textSize = 20f; setTypeface(typeface, android.graphics.Typeface.BOLD); text = fallbackTitle }
        meta = TextView(this).apply { textSize = 13f; setPadding(0, dp(4), 0, 0) }
        cover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0x22888888)
            layoutParams = LinearLayout.LayoutParams(dp(110), dp(160))
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
            addView(titleView)
            addView(meta)
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; addView(cover); addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
        desc = TextView(this).apply { textSize = 14f; setPadding(0, dp(12), 0, dp(12)) }
        status = TextView(this).apply { text = "Loading…"; textSize = 14f; setPadding(0, dp(6), 0, dp(6)) }
        header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), 0)
            addView(top)
            addView(desc)
            addView(status)
        }
        list = ListView(this).apply { addHeaderView(header, null, false); adapter = ArrayAdapter<String>(this@MangaDetailActivity, android.R.layout.simple_list_item_1, emptyList<String>()) }
        setContentView(list)

        val ctx = this
        thread {
            val src = try { MihonBridge.sourceById(ctx, sourceId) } catch (t: Throwable) { null }
            val d = try {
                if (src == null) null else MihonBridge.details(src, url, fallbackTitle)
            } catch (t: Throwable) {
                com.lagradost.shiro.utils.mvvm.logError(t)
                null
            }
            runOnUiThread {
                if (src == null || d == null) {
                    status.text = "Could not load this manga. Check your connection or try another source."
                    return@runOnUiThread
                }
                details = d
                MangaSession.sourceId = sourceId
                MangaSession.mangaTitle = d.title
                MangaSession.chapters = d.chapters
                title = d.title
                titleView.text = d.title
                meta.text = listOfNotNull(
                    d.author?.takeIf { it.isNotBlank() },
                    when (d.status) { 1 -> "Ongoing"; 2 -> "Completed"; 3 -> "Licensed"; 4 -> "Publishing finished"; 5 -> "Cancelled"; 6 -> "On hiatus"; else -> null },
                    d.genre?.takeIf { it.isNotBlank() }
                ).joinToString("\n")
                desc.text = d.description.orEmpty()
                MangaSession.loadCover(src, d.thumbnail ?: intent.getStringExtra("thumb"), cover, 400)
                status.text = if (d.chapters.isEmpty()) "No chapters found" else "${d.chapters.size} chapters"
                bindChapters()
            }
        }
    }

    private fun bindChapters() {
        val d = details ?: return
        val labels = d.chapters.map { (if (it.url in read) "✓  " else "") + it.name.ifBlank { "Chapter ${it.number}" } }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        list.setOnItemClickListener { _, _, pos, _ ->
            val index = pos - list.headerViewsCount
            if (index in d.chapters.indices) {
                startActivity(Intent(this, MangaReaderActivity::class.java).putExtra("index", index))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        read.clear()
        read.addAll(getSharedPreferences("manga_read", MODE_PRIVATE).getStringSet("urls", emptySet())!!)
        bindChapters()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
