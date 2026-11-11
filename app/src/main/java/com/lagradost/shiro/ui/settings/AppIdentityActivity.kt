package com.lagradost.shiro.ui.settings

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.jaredrummler.cyanea.app.CyaneaAppCompatActivity
import com.lagradost.shiro.utils.AppIdentity

/** Settings -> App name and icon: custom name and icon with a live preview, applied as a shortcut and in recent apps. */
class AppIdentityActivity : CyaneaAppCompatActivity() {
    private var pendingIcon: Bitmap? = null
    private lateinit var preview: ImageView
    private lateinit var previewLabel: TextView
    private lateinit var nameField: EditText

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        val bmp = AppIdentity.load(this, uri)
        if (bmp == null) toast("That picture could not be read.") else { pendingIcon = bmp; refreshPreview() }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun refreshPreview() {
        val label = nameField.text.toString().trim().ifBlank { AppIdentity.defaultName(this) }
        previewLabel.text = label
        val bmp = pendingIcon ?: AppIdentity.icon(this)
        if (bmp != null) preview.setImageBitmap(bmp) else preview.setImageDrawable(packageManager.getApplicationIcon(packageName))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "App name and icon"
        val b = PageBuilder(this)
        val r = b.root

        r.addView(b.text("Choose a name and icon for ${AppIdentity.defaultName(this)}. They are saved on this device and kept across updates. This is separate from NEX.", 13f, dim = true))
        if (AppIdentity.isTv(this) || !AppIdentity.pinSupported(this)) {
            r.addView(b.text("Android TV and some launchers cannot add shortcuts to the home screen or change the app tile. Your name and icon still apply in the recent-apps card and as a long-press shortcut where the launcher supports it.", 12f, dim = true).apply { setPadding(0, b.dp(8), 0, 0) })
        }

        preview = ImageView(this).apply { layoutParams = LinearLayout.LayoutParams(b.dp(96), b.dp(96)); scaleType = ImageView.ScaleType.FIT_CENTER }
        previewLabel = TextView(this).apply { textSize = 14f; setTextColor(b.fg); gravity = Gravity.CENTER; setPadding(0, b.dp(8), 0, 0); maxLines = 1 }
        val previewBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, b.dp(12), 0, b.dp(12))
            addView(preview); addView(previewLabel)
        }
        nameField = EditText(this).apply {
            hint = "App name"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
            setText(AppIdentity.name(this@AppIdentityActivity) ?: "")
            setTextColor(b.fg)
            setHintTextColor(b.fg and 0x00FFFFFF or 0x66000000)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = refreshPreview()
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
        }
        r.addView(b.heading("Preview"))
        r.addView(b.card(previewBox, nameField))
        refreshPreview()

        // Preset names — quick picks that stay close to the real name (Neko included).
        r.addView(b.heading("Name presets"))
        val nameRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Nekomo", "Neko", "NekoHub", "AnimeNeko", "Neko Stream", "Neko Club").forEach { n ->
            nameRow.addView(b.primaryButton(n) { nameField.setText(n) })
        }
        r.addView(android.widget.HorizontalScrollView(this).apply { addView(nameRow) })

        // Real launcher disguise — activity-aliases swap the tile's name + icon system-wide.
        if (!AppIdentity.isTv(this)) {
            r.addView(b.heading("Launcher name & icon"))
            r.addView(b.text("Changes the app's own launcher tile. The launcher updates it a few seconds after you pick.", 12f, dim = true).apply { setPadding(0, 0, 0, b.dp(8)) })
            val aliasRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            AppIdentity.aliases.forEach { (id, label) ->
                aliasRow.addView(b.primaryButton(label) {
                    AppIdentity.applyAlias(this, id)
                    toast("Launcher tile set to \"$label\"")
                })
            }
            r.addView(android.widget.HorizontalScrollView(this).apply { addView(aliasRow) })
        }

        // Seasonal icon presets — the Nekomo mark recolored onto a themed tile.
        r.addView(b.heading("Icon presets"))
        val iconRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            "Sakura" to 0xFFE88BA7.toInt(), "Winter" to 0xFF7AA7E8.toInt(),
            "Spooky" to 0xFFE8863A.toInt(), "Midnight" to 0xFF2B2140.toInt()
        ).forEach { (label, color) ->
            iconRow.addView(b.primaryButton(label) {
                pendingIcon = presetIcon(color)
                refreshPreview()
            })
        }
        r.addView(android.widget.HorizontalScrollView(this).apply { addView(iconRow) })

        r.addView(b.primaryButton("Choose icon picture") { picker.launch("image/*") })
        r.addView(b.primaryButton("Apply") {
            AppIdentity.save(this, nameField.text.toString(), pendingIcon)
            pendingIcon = null
            AppIdentity.applyTaskDescription(this)
            refreshPreview()
            toast("Saved. It now shows in recent apps and the long-press shortcut. Use \"Add to home screen\" to place it on your home screen.")
        })
        r.addView(b.primaryButton("Add to home screen") {
            AppIdentity.save(this, nameField.text.toString(), pendingIcon); pendingIcon = null
            if (AppIdentity.requestPin(this)) toast("Confirm in the launcher to add the shortcut.")
            else toast("This launcher cannot add shortcuts. Your name and icon still apply in recent apps and the long-press shortcut.")
        })
        r.addView(b.primaryButton("Reset to default") {
            AppIdentity.reset(this)
            pendingIcon = null
            nameField.setText("")
            refreshPreview()
            toast("Back to the original name and icon. Remove any pinned shortcut from your home screen.")
        })
        setContentView(b.finish())
        // Changing the name or icon needs its own puzzle (separate from the screen-capture one)
        com.lagradost.shiro.ui.settings.PuzzleUi.require(this, com.lagradost.shiro.utils.Puzzle.Kind.IDENTITY, "App name and icon", onCancel = { finish() }) { }
    }

    /** Seasonal tile: rounded square in [bg] with the default app icon drawn inset on top. */
    private fun presetIcon(bg: Int): Bitmap {
        val size = 192
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = bg
        canvas.drawRoundRect(android.graphics.RectF(0f, 0f, size.toFloat(), size.toFloat()), size * 0.22f, size * 0.22f, paint)
        val icon = packageManager.getApplicationIcon(packageName)
        val inset = (size * 0.14f).toInt()
        icon.setBounds(inset, inset, size - inset, size - inset)
        icon.draw(canvas)
        return AppIdentity.prepareIcon(out)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
