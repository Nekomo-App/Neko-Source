package com.lagradost.shiro.utils

import android.app.Activity
import android.app.Dialog
import android.content.Context
import com.lagradost.shiro.AcraApplication
import android.view.View
import androidx.annotation.IdRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView

/*
 * Drop-in replacement for kotlinx.android.synthetic view lookups.
 * Resolves a view by id on the receiver, throwing on missing views like synthetics did.
 */
@Suppress("UNCHECKED_CAST")
fun <T : View> View.fv(@IdRes id: Int): T = findViewById(id) as T

@Suppress("UNCHECKED_CAST")
fun <T : View> Activity.fv(@IdRes id: Int): T = findViewById(id) as T

/**
 * Callbacks can fire after a fragment's view is destroyed (back navigation, async results).
 * Instead of crashing in requireView(), hand back a detached throwaway view of the requested
 * type so the stray update is a harmless no-op.
 */
@PublishedApi
internal fun <T : View> detachedView(cls: Class<T>, fragment: Fragment): T? = try {
    val ctx = fragment.context ?: AcraApplication.context ?: throw IllegalStateException("No context")
    cls.getConstructor(Context::class.java).newInstance(ctx)
} catch (t: Throwable) {
    null
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T : View> Fragment.fv(@IdRes id: Int): T =
    (view?.findViewById<View>(id) as? T) ?: detachedView(T::class.java, this)
    ?: throw IllegalStateException("View $id is not available (fragment view destroyed?)")

@Suppress("UNCHECKED_CAST")
fun <T : View> Dialog.fv(@IdRes id: Int): T = findViewById(id) as T

@Suppress("UNCHECKED_CAST")
inline fun <reified T : View> DialogFragment.fv(@IdRes id: Int): T =
    (dialog?.findViewById<View>(id) as? T) ?: detachedView(T::class.java, this)
    ?: throw IllegalStateException("View $id is not available (dialog destroyed?)")

@Suppress("UNCHECKED_CAST")
fun <T : View> RecyclerView.ViewHolder.fv(@IdRes id: Int): T = itemView.findViewById(id) as T
