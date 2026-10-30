package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import com.kazumaproject.markdownhelperkeyboard.R
import androidx.navigation.fragment.findNavController
import timber.log.Timber

internal fun Fragment.navigateSafely(@IdRes resId: Int): Boolean {
    return navigateSafely(resId, null)
}

internal fun Fragment.navigateSafely(@IdRes resId: Int, args: Bundle?): Boolean {
    return runCatching {
        findNavController().navigateSafely(resId, args)
    }.getOrElse { error ->
        Timber.w(error, "Failed to obtain NavController for navigation target: %s", resId)
        false
    }
}

internal fun NavController.navigateSafely(@IdRes resId: Int): Boolean {
    return navigateSafely(resId, null)
}

internal fun NavController.navigateSafely(@IdRes resId: Int, args: Bundle?): Boolean {
    val destination = currentDestination ?: return false
    val hasAction = destination.getAction(resId) != null || graph.getAction(resId) != null
    val hasDestination = graph.findNode(resId) != null

    if (!hasAction && !hasDestination) {
        Timber.w(
            "Ignored navigation because action/destination was not available. current=%s target=%s",
            destination.safeLogName(),
            resId
        )
        return false
    }

    if (!hasAction && destination.id == resId) {
        return false
    }

    return runCatching {
        navigate(resId, args, settingsNavOptions)
        true
    }.getOrElse { error ->
        Timber.w(
            error,
            "Ignored duplicate or stale navigation. current=%s target=%s",
            destination.safeLogName(),
            resId
        )
        false
    }
}

/** 設定画面の遷移アニメーション（右から少しずれて浮かぶ／戻るときは逆）。 */
internal val settingsNavOptions: NavOptions = NavOptions.Builder()
    .setEnterAnim(R.anim.settings_enter)
    .setExitAnim(R.anim.settings_exit)
    .setPopEnterAnim(R.anim.settings_pop_enter)
    .setPopExitAnim(R.anim.settings_pop_exit)
    .build()

private fun NavDestination.safeLogName(): String {
    return label?.toString() ?: id.toString()
}
