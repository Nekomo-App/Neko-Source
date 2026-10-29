package com.lagradost.shiro.ui.library

import ANILIST_TOKEN_KEY
import DataStore.getKey
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.jaredrummler.cyanea.app.CyaneaAppCompatActivity
import com.lagradost.shiro.ui.MainActivity
import com.lagradost.shiro.utils.ANILIST_ACCOUNT_ID
import com.lagradost.shiro.utils.LocalLibrary
import kotlin.concurrent.thread

/**
 * Offline library for users who are not signed in. Entries live on this device only; they can be exported,
 * imported on another device, or pushed to an AniList account once signed in.
 */
class LocalLibraryActivity : CyaneaAppCompatActivity() {
    private lateinit var list: LinearLayout
    private lateinit var filters: LinearLayout
    private var filter: String? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Offline library"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)) }
        root.addView(TextView(this).apply {
            text = "Saved on this device only. Export it to move it to another device, or sign in and sync it to your account."
            textSize = 13f
            setPadding(dp(4), dp(4), dp(4), dp(8))
        })
        filters = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(HorizontalScrollView(this).apply { addView(filters); isHorizontalScrollBarEnabled = false })

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        actions.addView(actionButton("Export") { export() })
        actions.addView(actionButton("Import") { pickFile() })
        actions.addView(actionButton("Sync to AniList") { syncToAniList() })
        root.addView(actions)
        setContentView(root)
        refresh()
    }

    private fun actionButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun refresh() {
        val all = LocalLibrary.all(this)
        filters.removeAllViews()
        val names = listOf<String?>(null) + LocalLibrary.STATUSES
        for (n in names) {
            val count = if (n == null) all.size else all.count { it.status == n }
            filters.addView(Button(this).apply {
                text = "${n ?: "All"} ($count)"
                isAllCaps = false
                alpha = if (filter == n) 1f else 0.55f
                setOnClickListener { filter = n; refresh() }
            })
        }
        list.removeAllViews()
        val shown = all.filter { filter == null || it.status == filter }
        if (shown.isEmpty()) {
            list.addView(TextView(this).apply {
                text = if (all.isEmpty()) "Nothing here yet. Open a title and tap \"Save to offline library\"." else "No titles with this status."
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(48), dp(16), dp(16))
            })
            return
        }
        for (e in shown) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(6), dp(4), dp(6))
                isClickable = true
                isFocusable = true
                setOnClickListener { open(e.slug, e.title) }
                setOnLongClickListener { editEntry(e); true }
            }
            row.addView(ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor("#22000000"))
                layoutParams = LinearLayout.LayoutParams(dp(56), dp(80))
                if (!e.poster.isNullOrBlank()) Glide.with(this@LocalLibraryActivity).load(e.poster).into(this)
            })
            val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(8), 0) }
            text.addView(TextView(this).apply { this.text = e.title; textSize = 16f; maxLines = 2 })
            text.addView(TextView(this).apply { this.text = e.status + (if (e.progress > 0) " · episode ${e.progress}" else ""); textSize = 13f; alpha = 0.7f })
            row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Button(this).apply {
                this.text = "Edit"
                isAllCaps = false
                setOnClickListener { editEntry(e) }
            })
            list.addView(row)
        }
    }

    private fun editEntry(e: LocalLibrary.Entry) {
        val options = LocalLibrary.STATUSES + "Remove from library"
        AlertDialog.Builder(this)
            .setTitle(e.title)
            .setItems(options.toTypedArray()) { _, which ->
                if (which < LocalLibrary.STATUSES.size) LocalLibrary.set(this, e.slug, e.title, e.poster, options[which])
                else LocalLibrary.remove(this, e.slug)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun open(slug: String, name: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("open_slug", slug)
                .putExtra("open_name", name)
        )
        finish()
    }

    private fun export() {
        if (LocalLibrary.all(this).isEmpty()) {
            Toast.makeText(this, "Nothing to export yet", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Nekomo offline library")
                    .putExtra(Intent.EXTRA_TEXT, LocalLibrary.exportJson(this)),
                "Export offline library"
            )
        )
    }

    private fun pickFile() {
        try {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_IMPORT
            )
        } catch (e: Exception) {
            Toast.makeText(this, "No file picker available", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_IMPORT || resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        thread {
            val msg = try {
                val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                "Imported ${LocalLibrary.importJson(this, json)} title(s)"
            } catch (e: Exception) {
                "That file is not a Nekomo library export"
            }
            runOnUiThread {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                refresh()
            }
        }
    }

    private fun syncToAniList() {
        val signedIn = getKey<String>(ANILIST_TOKEN_KEY, ANILIST_ACCOUNT_ID, null) != null
        if (!signedIn) {
            Toast.makeText(this, "Sign in to AniList first (Settings → Accounts or the welcome screen)", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "Syncing…", Toast.LENGTH_SHORT).show()
        thread {
            val (ok, skipped) = LocalLibrary.syncToAniList(this)
            runOnUiThread {
                Toast.makeText(
                    this,
                    "Synced $ok title(s)" + if (skipped > 0) ". $skipped from extensions can't be synced (not on AniList)" else "",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        private const val REQ_IMPORT = 4711
    }
}
