package com.lagradost.shiro.ui.tv
import com.lagradost.shiro.utils.fv

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.Transition
import androidx.transition.TransitionManager
import com.google.android.material.button.MaterialButton
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.GlideApp
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.lagradost.shiro.utils.safeNavigate
import com.lagradost.shiro.ui.home.HomeFragment.Companion.homeViewModel
import com.lagradost.shiro.ui.home.HomeViewModel
import com.lagradost.shiro.ui.home.MasterCardAdapter
import com.lagradost.shiro.utils.AppUtils.getColorFromAttr
import com.lagradost.shiro.utils.AppUtils.getCurrentActivity
import com.lagradost.shiro.utils.AppUtils.loadPage
import com.lagradost.shiro.utils.AppUtils.observe
import com.lagradost.shiro.utils.NekomoApi
import com.lagradost.shiro.utils.NekomoApi.Companion.initShiroApi
import com.lagradost.shiro.utils.NekomoApi.Companion.requestHome
import kotlin.concurrent.thread


class MainFragment : Fragment() {

    // Featured hero rotation: the curated random pick plus trending titles that ship
    // banner art, cross-faded every HERO_ROTATE_MS like the mobile hero's feature slot.
    private var heroItems: List<NekomoApi.Companion.Data> = emptyList()
    private var heroIndex = 0
    private val heroHandler = Handler(Looper.getMainLooper())
    private val heroTick = object : Runnable {
        override fun run() {
            if (heroItems.size > 1) {
                // Hold position while a hero button is focused so a swap cannot yank focus.
                val held = fv<MaterialButton>(R.id.tv_hero_watch)?.isFocused == true ||
                    fv<MaterialButton>(R.id.tv_hero_info)?.isFocused == true
                if (!held) showHero((heroIndex + 1) % heroItems.size)
                heroHandler.postDelayed(this, HERO_ROTATE_MS)
            }
        }
    }

    private fun showHero(index: Int) {
        val ctx = context ?: return
        val item = heroItems.getOrNull(index) ?: return
        heroIndex = index
        fv<FrameLayout>(R.id.tv_hero)?.visibility = VISIBLE
        fv<TextView>(R.id.tv_hero_title)?.text =
            item.title_english?.takeIf { it.isNotBlank() } ?: item.title
        fv<TextView>(R.id.tv_hero_kicker)?.text =
            item.native_title.takeIf { it.isNotBlank() && it != item.title } ?: item.format
        fv<TextView>(R.id.tv_hero_synopsis)?.text = item.synopsis.trim()
        val art = item.banner.takeIf { it.isNotBlank() } ?: item.poster
        fv<ImageView>(R.id.tv_banner)?.let { banner ->
            GlideApp.with(ctx)
                .load(NekomoApi.getFullUrlCdn(art))
                .timeout(10000)
                .transition(DrawableTransitionOptions.withCrossFade(300))
                .into(banner)
        }
        fv<MaterialButton>(R.id.tv_hero_watch)?.setOnClickListener {
            activity?.loadPage(item.slug, item.title)
        }
        fv<MaterialButton>(R.id.tv_hero_info)?.setOnClickListener {
            activity?.loadPage(item.slug, item.title)
        }
        fv<LinearLayout>(R.id.tv_hero_dots)?.let { dots ->
            dots.removeAllViews()
            val density = resources.displayMetrics.density
            repeat(heroItems.size) { i ->
                val dot = ImageView(ctx)
                dot.setImageResource(R.drawable.hero_dot)
                dot.alpha = if (i == index) 1.0f else 0.35f
                dot.layoutParams = LinearLayout.LayoutParams(
                    (10 * density).toInt(), (10 * density).toInt()
                ).apply { marginEnd = (6 * density).toInt() }
                dots.addView(dot)
            }
            dots.visibility = if (heroItems.size > 1) VISIBLE else GONE
        }
    }

    private fun homeLoaded(data: NekomoApi.ShiroHomePageNew?) {
        activity?.runOnUiThread {
            fv<android.widget.ProgressBar>(R.id.main_load)?.visibility = GONE
            fv<android.widget.Button>(R.id.main_reload_data_btt)?.visibility = GONE
            fv<androidx.leanback.widget.VerticalGridView>(R.id.vertical_grid_view).visibility = VISIBLE
            val featured = listOfNotNull(data?.random?.data) +
                (data?.trending?.data ?: emptyList()).filter { it.banner.isNotBlank() }
            val seen = mutableSetOf<String>()
            heroItems = featured.filter { it.slug.isNotBlank() && seen.add(it.slug) }.take(HERO_COUNT)
            heroIndex = 0
            if (heroItems.isEmpty()) {
                fv<FrameLayout>(R.id.tv_hero)?.visibility = GONE
            } else {
                showHero(0)
            }
            heroHandler.removeCallbacks(heroTick)
            if (heroItems.size > 1) heroHandler.postDelayed(heroTick, HERO_ROTATE_MS)
            val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder> = MasterCardAdapter(
                requireActivity(),
            )
            fv<androidx.leanback.widget.VerticalGridView>(R.id.vertical_grid_view)?.adapter = adapter
            (fv<androidx.leanback.widget.VerticalGridView>(R.id.vertical_grid_view)?.adapter as? MasterCardAdapter)?.notifyDataSetChanged()
            //val snapHelper = LinearSnapHelper()
            //snapHelper.attachToRecyclerView(fv<androidx.leanback.widget.VerticalGridView>(R.id.vertical_grid_view))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        homeViewModel =
            ViewModelProvider(getCurrentActivity()!!).get(HomeViewModel::class.java)
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_main_tv, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.let {
            it.window.setBackgroundDrawable(ColorDrawable(it.getColorFromAttr(R.attr.background)))
        }
        NekomoApi.onHomeError += ::onHomeErrorCatch
        if (NekomoApi.hasThrownError != -1) {
            onHomeErrorCatch(NekomoApi.hasThrownError == 1)
        }


        val focusListener = View.OnFocusChangeListener { v, hasFocus ->
            val transition: Transition = AutoTransition()
            transition.duration = 2000 // DURATION OF ANIMATION IN MS
            fv<android.widget.RelativeLayout>(R.id.tv_menu_bar)?.let {
                TransitionManager.beginDelayedTransition(it, transition)
            }
            val scale = if (hasFocus) 0.7f else 0.5f
            v?.scaleX = scale
            v?.scaleY = scale
        }

        fv<android.widget.ImageView>(R.id.search_icon).onFocusChangeListener = focusListener
        fv<android.widget.ImageView>(R.id.settings_icon).onFocusChangeListener = focusListener
        fv<android.widget.ImageView>(R.id.library_icon).onFocusChangeListener = focusListener
        fv<MaterialButton>(R.id.tv_hero_watch)?.onFocusChangeListener = focusListener
        fv<MaterialButton>(R.id.tv_hero_info)?.onFocusChangeListener = focusListener

        fv<android.widget.RelativeLayout>(R.id.tv_menu_bar).visibility = VISIBLE

        fv<android.widget.ImageView>(R.id.search_icon).setOnClickListener {
            findNavController().safeNavigate(
                R.id.global_to_navigation_search_tv
            )
        }
        fv<android.widget.ImageView>(R.id.library_icon).setOnClickListener {
            findNavController().safeNavigate(
                R.id.global_to_navigation_library_tv
            )
        }
        fv<android.widget.ImageView>(R.id.settings_icon).setOnClickListener {
            findNavController().safeNavigate(
                R.id.global_to_navigation_settings_tv
            )
        }
        homeViewModel?.apiData?.observe(viewLifecycleOwner) {
            homeLoaded(it)
        }
    }

    private fun onHomeErrorCatch(fullRe: Boolean) {
        // Null check because somehow this can crash
        activity?.runOnUiThread {
            // ?. because it somehow crashes anyways without it for one person
            if (fv<android.widget.Button>(R.id.main_reload_data_btt) != null) {
                fv<android.widget.Button>(R.id.main_reload_data_btt)?.visibility = VISIBLE
                fv<android.widget.ProgressBar>(R.id.main_load)?.visibility = GONE
                fv<android.widget.Button>(R.id.main_reload_data_btt)?.setOnClickListener {
                    fv<android.widget.Button>(R.id.main_reload_data_btt)?.visibility = GONE
                    fv<android.widget.ProgressBar>(R.id.main_load)?.visibility = VISIBLE
                    thread {
                        if (fullRe) {
                            context?.initShiroApi()
                        } else {
                            context?.requestHome(false)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        observe(homeViewModel!!.subscribed) {
            (fv<androidx.leanback.widget.VerticalGridView>(R.id.vertical_grid_view)?.adapter as? MasterCardAdapter)?.notifyDataSetChanged()
        }
        observe(homeViewModel!!.favorites) {
            (fv<androidx.leanback.widget.VerticalGridView>(R.id.vertical_grid_view)?.adapter as? MasterCardAdapter)?.notifyDataSetChanged()
        }
        thread {
            context?.requestHome()
        }
        homeViewModel!!.apiData.observe(viewLifecycleOwner) {
            homeLoaded(it)
        }
        Handler().postDelayed({
            fv<android.widget.ImageView>(R.id.search_icon)?.requestFocus()
        }, 200L)


        super.onResume()
    }

    override fun onDestroyView() {
        heroHandler.removeCallbacks(heroTick)
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        private const val HERO_ROTATE_MS = 8000L
        private const val HERO_COUNT = 5
        var hasBeenInPlayer: Boolean = false
        fun newInstance() =
            MainFragment().apply {
                arguments = Bundle().apply {
                }
            }
    }
}