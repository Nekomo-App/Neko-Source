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
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
