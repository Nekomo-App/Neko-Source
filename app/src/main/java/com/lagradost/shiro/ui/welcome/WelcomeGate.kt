package com.lagradost.shiro.ui.welcome

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.jaredrummler.cyanea.Cyanea
import com.lagradost.shiro.R
import com.lagradost.shiro.utils.AppUtils.openBrowser
import com.lagradost.shiro.utils.AniListApi.Companion.authenticateAniList
import com.lagradost.shiro.utils.MALApi.Companion.authenticateMAL
import com.lagradost.shiro.utils.auth.AuthManager
import com.lagradost.shiro.utils.auth.KitsuApi
import kotlin.concurrent.thread

/**
 * Full-screen, non-cancelable welcome/sign-in gate shown over MainActivity / TvActivity
 * until the user has a verified account and has accepted the community rules.
 *
 * It is a Dialog (own window) rather than a view overlay, so touch, D-pad focus and the
 * back key can not reach the app underneath. OAuth redirects still arrive at the
 * activity via onNewIntent/handleIntent, which stores the token + profile; this gate
 * simply polls AuthManager and advances when the profile has been verified.
 */
class WelcomeGate private constructor(private val activity: FragmentActivity) {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var dialog: Dialog
    private val root = LinearLayout(activity)
    private val ctx get() = activity
    private var step = Step.SIGN_IN
    private var statusView: TextView? = null
    private var hiddenForBrowser = false

    private enum class Step { SIGN_IN, RULES }

    private val bg = Cyanea.instance.backgroundColor
    private val fg = if (Cyanea.instance.isDark) Color.WHITE else Color.BLACK
    private val card = if (Cyanea.instance.isDark) 0x22FFFFFF else 0x14000000

    companion object {
        fun attachIfNeeded(activity: FragmentActivity) {
            if (AuthManager.isSignedIn(activity) && AuthManager.hasAcceptedRules(activity)) return
            WelcomeGate(activity).show()
        }

        /** Re-usable rules dialog for Settings -> Community Rules. */
        fun rulesBody(activity: FragmentActivity): Pair<String, String> =
            activity.getString(R.string.rules_title) to activity.getString(R.string.rules_body)
    }

    private fun show() {
        dialog = Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen).apply {
            setCancelable(false)
            setOnKeyListener { _, keyCode, event ->
                // Back must not reveal the app: leave it instead.
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    activity.finish()
                    true
                } else false
            }
            window?.apply {
                setBackgroundDrawable(ColorDrawable(bg))
                setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            }
        }
        val scroll = ScrollView(activity).apply { isFillViewport = true }
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setPadding(SocialLinks.dp(ctx, 24), SocialLinks.dp(ctx, 40), SocialLinks.dp(ctx, 24), SocialLinks.dp(ctx, 32))
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        dialog.setContentView(scroll)

        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                handler.removeCallbacksAndMessages(null)
                if (dialog.isShowing) dialog.dismiss()
            }
        })

        render()
        dialog.show()
        poll()
    }

    // ---- polling ----

    private fun poll() {
        handler.postDelayed({
            if (activity.isFinishing || activity.isDestroyed) return@postDelayed
            val signedIn = AuthManager.isSignedIn(ctx)
            if (signedIn && step == Step.SIGN_IN) {
                if (AuthManager.hasAcceptedRules(ctx)) {
                    dialog.dismiss(); return@postDelayed
                }
                step = Step.RULES
                restore(); render()
            } else if (!signedIn && hiddenForBrowser &&
                activity.supportFragmentManager.findFragmentByTag("WEB_VIEW") == null
            ) {
                // TV in-app browser was closed without finishing sign-in
                restore()
            }
            poll()
        }, 600)
    }

    private fun restore() {
        if (hiddenForBrowser) {
            hiddenForBrowser = false
            dialog.show()
        }
    }

    // ---- UI ----

    private fun render() {
        root.removeAllViews()
        if (step == Step.SIGN_IN) renderSignIn() else renderRules()
        root.post { root.findFocus() ?: root.getChildAt(0)?.requestFocus() }
    }

    private fun text(s: String, size: Float, bold: Boolean = false, alpha: Boolean = false) = TextView(ctx).apply {
        text = s
        textSize = size
        gravity = Gravity.CENTER
        setTextColor(if (alpha) (fg and 0x00FFFFFF or 0xB3000000.toInt()) else fg)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = SocialLinks.dp(ctx, 8) }
    }

    private fun button(label: String, color: Int, onClick: () -> Unit) = Button(ctx).apply {
        text = label
        isAllCaps = false
        textSize = 16f
        setTextColor(Color.WHITE)
        background = SocialLinks.cardBackground(ctx, color, 24)
        isFocusable = true
        stateListAnimator = null
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, SocialLinks.dp(ctx, 52)
        ).apply { bottomMargin = SocialLinks.dp(ctx, 10) }
        setOnClickListener { onClick() }
    }

    private fun linkText(label: String, onClick: () -> Unit) = TextView(ctx).apply {
        text = label
        textSize = 14f
        setTextColor(0xFF4FC3F7.toInt())
        paint.isUnderlineText = true
        setPadding(SocialLinks.dp(ctx, 10), SocialLinks.dp(ctx, 10), SocialLinks.dp(ctx, 10), SocialLinks.dp(ctx, 10))
        isFocusable = true
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun renderSignIn() {
        root.addView(ImageView(ctx).apply {
            setImageResource(if (Cyanea.instance.isDark) R.drawable.nekomo_logo_white else R.drawable.nekomo_logo)
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(SocialLinks.dp(ctx, 96), SocialLinks.dp(ctx, 96))
                .apply { bottomMargin = SocialLinks.dp(ctx, 12) }
        })
        root.addView(text(ctx.getString(R.string.app_name), 28f, bold = true))
        root.addView(text(ctx.getString(R.string.welcome_tagline), 14f, alpha = true).apply {
            (layoutParams as LinearLayout.LayoutParams).bottomMargin = SocialLinks.dp(ctx, 24)
        })

        root.addView(button("Sign in with AniList", 0xFF02A9FF.toInt()) { startBrowserSignIn { ctx.authenticateAniList() } })
        root.addView(button("Sign in with MyAnimeList", 0xFF2E51A2.toInt()) { startBrowserSignIn { ctx.authenticateMAL() } })
        root.addView(button("Continue with Kitsu", 0xFFF75239.toInt()) { showKitsuDialog() })
        statusView = text("", 13f, alpha = true).also { root.addView(it) }

        root.addView(linkText("Create an account") { showCreateAccount() })

        val legal = LinearLayout(ctx).apply { gravity = Gravity.CENTER }
        legal.addView(linkText("Terms of Service") { SocialLinks.openUrl(ctx, ctx.getString(R.string.link_terms)) })
        legal.addView(linkText("Privacy Policy") { SocialLinks.openUrl(ctx, ctx.getString(R.string.link_privacy)) })
        root.addView(legal)

        root.addView(text("Please make sure to follow the community rules. You will be asked to accept them after signing in.", 12f, alpha = true).apply {
            setPadding(0, SocialLinks.dp(ctx, 12), 0, SocialLinks.dp(ctx, 12))
        })

        root.addView(text("Follow Us", 18f, bold = true).apply {
            setPadding(0, SocialLinks.dp(ctx, 12), 0, SocialLinks.dp(ctx, 4))
        })
        val social = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        SocialLinks.populate(social, fg, card)
        root.addView(social, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun renderRules() {
        val (title, body) = rulesBody(activity)
        root.addView(text("Community Rules", 24f, bold = true).apply {
            setPadding(0, SocialLinks.dp(ctx, 24), 0, SocialLinks.dp(ctx, 12))
        })
        root.addView(text(title, 18f, bold = true))
        root.addView(text(body, 15f, alpha = true).apply {
            setPadding(0, 0, 0, SocialLinks.dp(ctx, 24))
        })
        root.addView(button(ctx.getString(R.string.rules_button), 0xFF43A047.toInt()) {
            AuthManager.setRulesAccepted(ctx)
            handler.removeCallbacksAndMessages(null)
            dialog.dismiss()
        })
    }

    /**
     * AniList/MAL sign-in goes through the browser and returns via a shiroapp:// redirect.
     * On TV the login page opens as an in-app WebView fragment that sits under this
     * dialog's window, so the dialog is hidden while it is open (see poll()).
     */
    private fun startBrowserSignIn(launch: () -> Unit) {
        statusView?.text = "Waiting for sign-in to finish..."
        if (com.lagradost.shiro.ui.tv.TvActivity.tvActivity != null) {
            hiddenForBrowser = true
            dialog.hide()
        }
        launch()
    }

    private fun showCreateAccount() {
        val names = arrayOf("AniList", "MyAnimeList", "Kitsu")
        val urls = intArrayOf(R.string.link_signup_anilist, R.string.link_signup_mal, R.string.link_signup_kitsu)
        AlertDialog.Builder(ctx)
            .setTitle("Create an account")
            .setItems(names) { _, which -> SocialLinks.openUrl(ctx, ctx.getString(urls[which])) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showKitsuDialog() {
        val pad = SocialLinks.dp(ctx, 20)
        val form = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val user = EditText(ctx).apply {
            hint = "Kitsu email or username"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            maxLines = 1
        }
        val pass = EditText(ctx).apply {
            hint = "Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            maxLines = 1
        }
        form.addView(user)
        form.addView(pass)
        AlertDialog.Builder(ctx)
            .setTitle("Sign in with Kitsu")
            .setView(form)
            .setPositiveButton("Sign in") { _, _ ->
                statusView?.text = "Signing in..."
                thread {
                    val res = KitsuApi.login(ctx.applicationContext, user.text.toString(), pass.text.toString())
                    activity.runOnUiThread { statusView?.text = if (res.success) "" else res.message }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
