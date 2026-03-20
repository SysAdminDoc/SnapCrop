package com.sysadmindoc.snapcrop

import android.content.res.Resources

/**
 * Gets exact system bar pixel heights from device resources.
 * These match the regions captured in screenshots regardless of
 * whether bars are transparent, hidden, or using gesture navigation.
 */
object SystemBars {

    fun statusBarHeight(resources: Resources): Int {
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else 0
    }

    fun navigationBarHeight(resources: Resources): Int {
        val resId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else 0
    }
}
