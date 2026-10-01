package com.lagradost.shiro.ui.settings

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.jaredrummler.cyanea.Cyanea
import com.jaredrummler.cyanea.app.CyaneaAppCompatActivity
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.welcome.SocialLinks
import com.lagradost.shiro.ui.welcome.WelcomeGate

/** Settings -> Community Rules: the rules notice plus the Follow Us links. */
class CommunityRulesActivity : CyaneaAppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Community Rules"

        val fg = if (Cyanea.instance.isDark) Color.WHITE else Color.BLACK
        val card = if (Cyanea.instance.isDark) 0x22FFFFFF else 0x14000000
        val pad = SocialLinks.dp(this, 20)
        val (ruleTitle, ruleBody) = WelcomeGate.rulesBody(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun label(s: String, size: Float, bold: Boolean = false) = TextView(this).apply {
            text = s
            textSize = size
            setTextColor(fg)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, SocialLinks.dp(this@CommunityRulesActivity, 10))
        }
        root.addView(label(ruleTitle, 18f, true))
        root.addView(label(ruleBody, 15f))
        root.addView(label("Follow Us", 18f, true).apply { setPadding(0, pad, 0, SocialLinks.dp(context, 8)) })
        val social = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        SocialLinks.populate(social, fg, card)
        root.addView(social)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Cyanea.instance.backgroundColor)
            addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
