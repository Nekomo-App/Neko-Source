package com.lagradost.shiro.ui.home
import com.lagradost.shiro.utils.fv

import DataStore.getKey
import DataStore.mapper
import DataStore.setKey
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.view.setMargins
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.transition.ChangeBounds
import androidx.transition.Transition
import androidx.transition.TransitionManager
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.GlideApp
import com.lagradost.shiro.ui.MainActivity
import com.lagradost.shiro.ui.toPx
import com.lagradost.shiro.utils.AppUtils.displayCardData
import com.lagradost.shiro.utils.AppUtils.getCurrentActivity
import com.lagradost.shiro.utils.AppUtils.getNextEpisode
import com.lagradost.shiro.utils.AppUtils.guaranteedContext
import com.lagradost.shiro.utils.AppUtils.loadPage
import com.lagradost.shiro.utils.AppUtils.loadPlayer
import com.lagradost.shiro.utils.AppUtils.observe
import com.lagradost.shiro.utils.AppUtils.settingsManager
import com.lagradost.shiro.utils.PositionedCropTransformation
import com.lagradost.shiro.utils.ShiroApi
import com.lagradost.shiro.utils.ShiroApi.Companion.cachedHome
import com.lagradost.shiro.utils.ShiroApi.Companion.getAnimePageNew
import com.lagradost.shiro.utils.ShiroApi.Companion.getFullUrlCdn
import com.lagradost.shiro.utils.ShiroApi.Companion.getRandom
import com.lagradost.shiro.utils.ShiroApi.Companion.hasThrownError
import com.lagradost.shiro.utils.ShiroApi.Companion.initShiroApi
import com.lagradost.shiro.utils.ShiroApi.Companion.requestHome
import com.lagradost.shiro.utils.mvvm.logError
import com.lagradost.shiro.utils.mvvm.normalSafeApiCall
import kotlin.concurrent.thread

//const val MAXIMUM_FADE = 0.3f
//const val FADE_SCROLL_DISTANCE = 700f

class HomeFragment : Fragment() {
    companion object {
        var homeViewModel: HomeViewModel? = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        homeViewModel = homeViewModel ?: ViewModelProvider(getCurrentActivity()!!).get(HomeViewModel::class.java)

//        /** THIS FUCKS UP OTHER NON SHARED TRANSITIONS!!! */
//        exitTransition = Hold()
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    private fun homeLoaded(data: ShiroApi.ShiroHomePageNew?) {
        activity?.runOnUiThread {
            /*fv<androidx.recyclerview.widget.RecyclerView>(R.id.trending_anime_scroll_view).removeAllViews()
            fv<androidx.recyclerview.widget.RecyclerView>(R.id.recentlySeenScrollView).removeAllViews()
            fv<androidx.recyclerview.widget.RecyclerView>(R.id.recently_updated_scroll_view).removeAllViews()
            fv<androidx.recyclerview.widget.RecyclerView>(R.id.favouriteScrollView).removeAllViews()
            fv<androidx.recyclerview.widget.RecyclerView>(R.id.scheduleScrollView).removeAllViews()
*/
            //val cardInfo = data?.homeSlidesData?.shuffled()?.take(1)?.get(0)
            /*val glideUrl = GlideUrl("https://fastani.net/" + cardInfo?.bannerImage) { FastAniApi.currentHeaders }
            context?.let {
                GlideApp.with(it)
                    .load(glideUrl)
                    .into(fv<android.widget.ImageView>(R.id.main_backgroundImage))
            }*/


            //"http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

            /*fv<android.widget.ImageView>(R.id.main_poster).setOnClickListener {
                MainActivity.loadPage(cardInfo!!)
                // MainActivity.loadPlayer(0, 0, cardInfo!!)
            }*/

            if (settingsManager?.getBoolean("swipe_to_refresh", true) == true) {
                fv<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.home_swipe_refresh)?.isEnabled = true
                fv<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.home_swipe_refresh).setOnRefreshListener {
                    generateRandom()
                    fv<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.home_swipe_refresh).isRefreshing = false
                }
            } else {
                fv<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.home_swipe_refresh)?.isEnabled = false
            }


            generateRandom(data?.random)

            // TODO MAKE THIS LIKE MASTERCARDADAPTER
            if (data != null) {
                activity?.displayCardData(data.trending?.data?.map {
                    ShiroApi.CommonAnimePageData(
                        it.title,
                        it.poster,
                        it.slug,
                        it.title_english
                    )
                }, fv<androidx.recyclerview.widget.RecyclerView>(R.id.trending_anime_scroll_view), fv<android.widget.TextView>(R.id.trending_text))
                activity?.displayCardData(
                    data.recents?.map {
                        ShiroApi.CommonAnimePageData(
                            it.anime.title,
                            it.anime.poster,
                            it.anime.slug,
                        )
                    }?.distinctBy { it.slug },
                    fv<androidx.recyclerview.widget.RecyclerView>(R.id.recently_updated_scroll_view),
                    fv<android.widget.TextView>(R.id.recently_updated_text)
                )
//                activity?.displayCardData(data.data.ongoing_animes, ongoing_anime_scroll_view, ongoing_anime_text)
//                activity?.displayCardData(data.data.latest_animes, latest_anime_scroll_view, latest_anime_text)
            }
            //displayCardData(data?.recentlyAddedData, recentScrollView)
            displayFav()
            displaySubbed()

            /*
            if (data?.schedule?.isNotEmpty() == true) {
                fv<android.widget.LinearLayout>(R.id.scheduleRoot).visibility = VISIBLE
                //println(data.favorites!!.map { it?.title?.english})
                displayCardData(data.schedule, fv<androidx.recyclerview.widget.RecyclerView>(R.id.scheduleScrollView))
            } else {
                fv<android.widget.LinearLayout>(R.id.scheduleRoot).visibility = GONE
            }

*/
            val transition: Transition = ChangeBounds()
            transition.duration = 100
            if (data?.recentlySeen?.isNotEmpty() == true) {
                fv<android.widget.LinearLayout>(R.id.recentlySeenRoot).visibility = VISIBLE
                //println(data.recentlySeen)
                activity?.displayCardData(data.recentlySeen, fv<androidx.recyclerview.widget.RecyclerView>(R.id.recentlySeenScrollView))
            } else {
                fv<android.widget.LinearLayout>(R.id.recentlySeenRoot).visibility = GONE
            }
            TransitionManager.beginDelayedTransition(fv<com.nirhart.parallaxscroll.views.ParallaxScrollView>(R.id.main_scroll), transition)
            fv<android.widget.ProgressBar>(R.id.main_load)?.alpha = 0f
            fv<com.nirhart.parallaxscroll.views.ParallaxScrollView>(R.id.main_scroll)?.alpha = 1f

            fv<android.widget.Button>(R.id.main_reload_data_btt)?.alpha = 0f
            fv<android.widget.Button>(R.id.main_reload_data_btt)?.isClickable = false
            fv<android.widget.LinearLayout>(R.id.main_layout)?.setPadding(0, MainActivity.statusHeight, 0, 0)
        }
    }

    private fun generateRandom(randomPage: ShiroApi.Companion.Random? = null) {
        thread {
            val random: ShiroApi.Companion.Random? = randomPage ?: getRandom()
            cachedHome?.random = random
            val randomData = random?.data
            // Hack, assuming all dubbed shows have a normal equivalent
            /*
            val hideDubbed = settingsManager!!.getBoolean("hide_dubbed", false)
            if (hideDubbed && randomData != null) {
                randomData.slug = randomData.slug.removeSuffix("-dubbed")
                randomData.name = randomData.name.removeSuffix("Dubbed")
            }*/
            activity?.runOnUiThread {
                try {
                    if (randomData != null) {
                        // This can throw NPE as fv<android.widget.LinearLayout>(R.id.main_layout) isn't guaranteed to be inflated
                        val transition: Transition = ChangeBounds()
                        transition.duration = 100 // DURATION OF ANIMATION IN MS
                        TransitionManager.beginDelayedTransition(fv<android.widget.LinearLayout>(R.id.main_layout), transition)
                        fv<android.widget.FrameLayout>(R.id.main_poster_holder).visibility = VISIBLE
                        fv<android.widget.LinearLayout>(R.id.main_poster_text_holder).visibility = VISIBLE
                        val marginParams: FrameLayout.LayoutParams = FrameLayout.LayoutParams(
                            LinearLayoutCompat.LayoutParams.MATCH_PARENT, // view width
                            LinearLayoutCompat.LayoutParams.WRAP_CONTENT, // view height
                        )

                        marginParams.setMargins(0, 250.toPx, 0, 0)
                        fv<android.widget.LinearLayout>(R.id.main_layout).layoutParams = marginParams

                        val glideUrlMain = getFullUrlCdn(randomData.poster)
                        context?.let {
                            val settingsManager = PreferenceManager.getDefaultSharedPreferences(it)
                            val savingData = settingsManager.getBoolean("data_saving", false)
                            GlideApp.with(it)
                                .load(glideUrlMain)
                                .timeout(10000) // 10s
                                .transform(PositionedCropTransformation(1f, 0f))
                                .transition(DrawableTransitionOptions.withCrossFade(100))
                                .onlyRetrieveFromCache(savingData)
                                .into(fv<android.widget.ImageView>(R.id.main_poster))
                        }

                        fv<android.widget.TextView>(R.id.main_name)?.text = randomData.title
                        normalSafeApiCall {
                            fv<android.widget.TextView>(R.id.main_genres)?.text =
                                mapper.readValue<List<String>>(randomData.genres)
                                    .joinToString(prefix = "", postfix = "", separator = " • ")
                        }
                        fv<com.google.android.material.button.MaterialButton>(R.id.main_watch_button).setOnClickListener {
                            Toast.makeText(activity, "Loading link", Toast.LENGTH_SHORT).show()
                            thread {
                                try {
                                    // LETTING USER PRESS STUFF WHEN THIS LOADS CAN CAUSE BUGS
                                    val page = getAnimePageNew(randomData.slug)
                                    if (page != null) {
                                        val nextEpisode = context?.getNextEpisode(page.data)
                                        nextEpisode?.let {
                                            activity?.loadPlayer(nextEpisode.episodeIndex, 0L, page.data)
                                        }
                                    } else {
                                        activity?.runOnUiThread {
                                            Toast.makeText(activity, "Loading link failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    logError(e)
                                    activity?.runOnUiThread {
                                        Toast.makeText(activity, "Loading link failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        fv<com.google.android.material.button.MaterialButton>(R.id.main_watch_button).setOnLongClickListener {
                            //MainActivity.loadPage(cardInfo!!)
                            val page = getAnimePageNew(randomData.slug)
                            val nextEpisode = page?.data?.let { it1 -> context?.getNextEpisode(it1) }
                            if (nextEpisode != null) {
                                Toast.makeText(
                                    activity,
                                    "Episode ${nextEpisode.episodeIndex + 1}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            return@setOnLongClickListener true
                        }
                        fv<com.google.android.material.button.MaterialButton>(R.id.main_info_button).setOnClickListener {
                            activity?.loadPage(randomData.slug, randomData.title)
                        }
                    } else {
                        fv<android.widget.FrameLayout>(R.id.main_poster_holder).visibility = GONE
                        fv<android.widget.LinearLayout>(R.id.main_poster_text_holder).visibility = GONE
                        val marginParams: FrameLayout.LayoutParams = FrameLayout.LayoutParams(
                            LinearLayoutCompat.LayoutParams.MATCH_PARENT, // view width
                            LinearLayoutCompat.LayoutParams.WRAP_CONTENT, // view height
                        )

                        marginParams.setMargins(0)
                        fv<android.widget.LinearLayout>(R.id.main_layout).layoutParams = marginParams
                    }
                } catch (e: Exception) {
                    // e.g. IllegalStateException from fv() when the fragment's view is already
                    // gone (user navigated away while this callback was in flight)
                    logError(e)
                }


            }
        }


    }

    private fun onHomeErrorCatch(fullRe: Boolean) {
        activity?.runOnUiThread {
            if (fv<android.widget.Button>(R.id.main_reload_data_btt) != null) {
                fv<android.widget.Button>(R.id.main_reload_data_btt)?.alpha = 1f
                fv<android.widget.ProgressBar>(R.id.main_load)?.alpha = 0f
                fv<android.widget.Button>(R.id.main_reload_data_btt)?.isClickable = true
                fv<android.widget.Button>(R.id.main_reload_data_btt)?.setOnClickListener {
                    fv<android.widget.Button>(R.id.main_reload_data_btt)?.alpha = 0f
                    fv<android.widget.ProgressBar>(R.id.main_load)?.alpha = 1f
                    fv<android.widget.Button>(R.id.main_reload_data_btt)?.isClickable = false
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


    private fun displayFav() {
        val favorites = homeViewModel!!.favorites.value
        activity?.runOnUiThread {
            // RELOAD ON NEW FAV!
            if (favorites?.isNotEmpty() == true) {
                fv<android.widget.LinearLayout>(R.id.favouriteRoot).visibility = VISIBLE
                //println(data.favorites!!.map { it?.title?.english})
                activity?.displayCardData(
                    favorites.sortedWith(compareBy { it?.name }).mapNotNull { it }.toList(),
                    fv<androidx.recyclerview.widget.RecyclerView>(R.id.favouriteScrollView),
                    fv<android.widget.TextView>(R.id.favorites_text),
                    overrideHideDubbed = true
                )
            } else {
                fv<android.widget.LinearLayout>(R.id.favouriteRoot).visibility = GONE
            }
        }
    }

    private fun displaySubbed() {
        if (settingsManager?.getBoolean("show_subscribed", true) == true) {
            val subscribed = homeViewModel!!.subscribed.value
            activity?.runOnUiThread {
                if (subscribed?.isNotEmpty() == true) {
                    fv<android.widget.LinearLayout>(R.id.subscribedRoot).visibility = VISIBLE
                    //println(data.favorites!!.map { it?.title?.english})
                    activity?.displayCardData(
                        subscribed.sortedWith(compareBy { it?.name }).mapNotNull { it }.toList(),
                        fv<androidx.recyclerview.widget.RecyclerView>(R.id.subscribedScrollView),
                        fv<android.widget.TextView>(R.id.subscribed_text),
                        overrideHideDubbed = true
                    )
                } else {
                    fv<android.widget.LinearLayout>(R.id.subscribedRoot).visibility = GONE
                }
            }
        }
    }


    override fun onResume() {
        observe(homeViewModel!!.subscribed) {
            displaySubbed()
        }
        observe(homeViewModel!!.favorites) {
            displayFav()
        }

        super.onResume()
    }

    override fun onDestroy() {
        ShiroApi.onHomeError -= ::onHomeErrorCatch
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fv<com.nirhart.parallaxscroll.views.ParallaxScrollView>(R.id.main_scroll)?.alpha = 0f
        ShiroApi.onHomeError += ::onHomeErrorCatch
        if (hasThrownError != -1) {
            onHomeErrorCatch(hasThrownError == 1)
        }

        homeViewModel!!.apiData.let {
            it.observe(viewLifecycleOwner) { homePage ->
                homeLoaded(homePage)
            }
            if (it.value != null && fv<android.widget.ProgressBar>(R.id.main_load)?.alpha == 1.0f) {
                homeLoaded(it.value)
            }
        }

        // When the home is gotten but home fragment isn't started
        if (homeViewModel?.apiData?.value == null && cachedHome != null) {
            homeLoaded(cachedHome)
        }

        // This gets overwritten when data is loaded
        fv<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.home_swipe_refresh)?.setOnRefreshListener {
            fv<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.home_swipe_refresh)?.isRefreshing = false
        }



        if (guaranteedContext(context).getKey("DMCA_MESSAGE", false) == false) {
            guaranteedContext(context).setKey("DMCA_MESSAGE", true)
            AlertDialog.Builder(guaranteedContext(context), R.style.AlertDialogCustom)
                .setCancelable(false)
                .setTitle("DMCA DISCLAIMER")
                .setPositiveButton("I understand") { dialogInterface, _ ->
                    dialogInterface.dismiss()
                }
                .setMessage("The Shiro app is only a front-end to the shiro site available in the browser. As it's only a front-end it does not host nor control the videos accessible in the app. The legality of the content shown in the app is therefore the responsibility of the video hosts. It's also the users responsibility to make sure that their usage of this app is legal in their country. Use this app at your own risk!\n\nIn case of copyright infringement contact the offending video hosting provider!\n\nThis app is only for personal and educational use.\n")
                .show()
        }

        // CAUSES CRASH ON 6.0.0
        /*fv<com.nirhart.parallaxscroll.views.ParallaxScrollView>(R.id.main_scroll).setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
               val fade = (FADE_SCROLL_DISTANCE - scrollY) / FADE_SCROLL_DISTANCE
               // COLOR ARGB INTRODUCED IN 26!
               val gray: Int = Color.argb(fade, 0f, fade, 0f)
            //   fv<android.widget.ImageView>(R.id.main_backgroundImage).alpha = maxOf(0f, MAXIMUM_FADE * fade) // DON'T DUE TO ALPHA FADING HINDERING FOREGROUND GRADIENT
        }*/

    }
}
