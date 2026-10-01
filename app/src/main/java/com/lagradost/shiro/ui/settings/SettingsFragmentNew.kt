package com.lagradost.shiro.ui.settings
import com.lagradost.shiro.utils.fv

import ANILIST_USER_KEY
import DataStore.getKey
import DataStore.getKeys
import MAL_TOKEN_KEY
import MAL_USER_KEY
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.*
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.jaredrummler.cyanea.Cyanea
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.GlideApp
import com.lagradost.shiro.ui.MainActivity
import com.lagradost.shiro.ui.MainActivity.Companion.statusHeight
import com.lagradost.shiro.ui.WebViewFragment.Companion.onWebViewNavigated
import com.lagradost.shiro.ui.tv.TvActivity.Companion.tvActivity
import com.lagradost.shiro.utils.AniListApi
import com.lagradost.shiro.utils.AppUtils.changeStatusBarState
import com.lagradost.shiro.utils.AppUtils.getCurrentActivity
import com.lagradost.shiro.utils.AppUtils.observe
import com.lagradost.shiro.utils.MALApi
import com.lagradost.shiro.utils.MALApi.Companion.getMalUser
import com.lagradost.shiro.utils.MAL_ACCOUNT_ID
import kotlin.concurrent.thread

class SettingsFragmentNew : Fragment() {
    companion object {
        var isInSettings: Boolean = false
        var restoreFileSelector: ActivityResultLauncher<String>? = null
        var settingsViewModel: SettingsViewModel? = null

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        /*val array = arrayOf(
            Pair("General", R.xml.settings_general),
            Pair("Style", R.xml.custom_pref_cyanea),
            Pair("Player", R.xml.settings_player),
            Pair("Accounts", R.xml.settings_accounts),
            Pair("History", R.xml.settings_history),
            Pair("Updates", R.xml.settings_update_info),
            Pair("About", R.xml.settings_about),
        )*/
        fv<android.widget.LinearLayout>(R.id.account_icon)?.isVisible = tvActivity == null
        val array = arrayOf(
            Pair(fv<android.widget.TextView>(R.id.settings_general), R.xml.settings_general),
            Pair(fv<android.widget.TextView>(R.id.settings_style), R.xml.custom_pref_cyanea),
            Pair(fv<android.widget.TextView>(R.id.settings_player), R.xml.settings_player),
            Pair(fv<android.widget.TextView>(R.id.settings_accounts), R.xml.settings_accounts),
            Pair(fv<android.widget.TextView>(R.id.settings_history), R.xml.settings_history),
            Pair(fv<android.widget.TextView>(R.id.settings_updates), R.xml.settings_update_info),
            Pair(fv<android.widget.TextView>(R.id.settings_about), R.xml.settings_about),
        )

        array.forEach { pair ->
            pair.first?.background = ColorDrawable(Cyanea.instance.backgroundColorDark)
            pair.first?.setOnClickListener {
                openSettingSubMenu(pair.second)
            }
        }

        fv<android.widget.TextView>(R.id.settings_integrations)?.apply {
            background = ColorDrawable(Cyanea.instance.backgroundColorDark)
            setOnClickListener {
                startActivity(Intent(getCurrentActivity(), IntegrationsActivity::class.java))
            }
        }

        // Extensions opens its own activity (repo + plugin management),
        // not a preference-xml submenu
        fv<android.widget.TextView>(R.id.settings_extensions)?.apply {
            background = ColorDrawable(Cyanea.instance.backgroundColorDark)
            setOnClickListener {
                startActivity(Intent(getCurrentActivity(), ExtensionsActivity::class.java))
            }
        }

        fv<android.widget.TextView>(R.id.settings_community_rules)?.apply {
            background = ColorDrawable(Cyanea.instance.backgroundColorDark)
            setOnClickListener {
                startActivity(Intent(getCurrentActivity(), CommunityRulesActivity::class.java))
            }
        }

        settingsViewModel =
            settingsViewModel ?: activity?.let {
                ViewModelProvider(it).get(
                    SettingsViewModel::class.java
                )
            }

        val topParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, // view width
            MainActivity.statusHeight // view height
        )
        fv<android.view.View>(R.id.top_padding_settings)?.layoutParams = topParams
        fv<android.widget.LinearLayout>(R.id.settings_root)?.background = ColorDrawable(Cyanea.instance.backgroundColor)

        if (tvActivity == null) {
            // Because the user isn't necessarily fetched
            if (context?.getKey<String>(MAL_TOKEN_KEY, MAL_ACCOUNT_ID, null) != null
                && context?.getKeys(MAL_USER_KEY)?.isEmpty() == true
            ) {
                thread {
                    context?.getMalUser()
                }
            }

            context?.let { context ->
                /*settings_listview?.adapter = ArrayAdapter(context, R.layout.listview_single_item, array.map { it.first })
                settings_listview.setOnItemClickListener { _, _, position, _ ->
                    openSettingSubMenu(array[position].second)
                }*/
                context.loadProfile()
                observe(settingsViewModel!!.hasLoggedIntoMAL) {
                    context.loadProfile()
                }
                observe(settingsViewModel!!.hasLoggedIntoAnilist) {
                    context.loadProfile()
                }


            }
        }
    }

    private fun Context.loadProfile() {
        var userImage: String? = null
        var userName: String? = null
        getKeys(ANILIST_USER_KEY).forEach { key ->
            getKey<AniListApi.AniListUser>(key, null)?.let {
                userImage = it.picture
                userName = it.name
            }
        }

        if (userImage == null || userName == null) {
            getKeys(MAL_USER_KEY).forEach { key ->
                getKey<MALApi.MalUser>(key, null)?.let {
                    userImage = userImage ?: it.picture
                    userName = userName ?: it.name
                }
            }
        }

        if (userName != null) {
            fv<android.widget.TextView>(R.id.name_text)?.text = userName
            fv<android.widget.TextView>(R.id.name_text)?.visibility = VISIBLE
        } else {
            fv<android.widget.TextView>(R.id.name_text)?.visibility = INVISIBLE
        }

        fv<android.widget.ImageView>(R.id.icon_image)?.setOnClickListener {
            fv<android.widget.TextView>(R.id.name_text)?.isVisible = !(fv<android.widget.TextView>(R.id.name_text)?.isVisible ?: false)
        }

        context?.let { context ->
            fv<android.widget.ImageView>(R.id.icon_image)?.let {
                GlideApp.with(context)
                    .load(userImage ?: "")
                    .transition(DrawableTransitionOptions.withCrossFade(100))
                    .error(R.drawable.nekomo_logo)
                    .into(it)
            }
        }
    }

    private fun openSettingSubMenu(xml: Int) {
        val intent = Intent(getCurrentActivity()!!, SettingsActivity::class.java).apply {
            putExtra(XML_KEY, xml)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isInSettings = false
    }

    override fun onResume() {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())
        activity?.changeStatusBarState(settingsManager.getBoolean("statusbar_hidden", true))?.let {
            statusHeight = it
        }
        isInSettings = true
        if (tvActivity != null) {
        } else {
            activity?.requestedOrientation = if (settingsManager.getBoolean("force_landscape", false)) {
                ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
        super.onResume()
    }

}