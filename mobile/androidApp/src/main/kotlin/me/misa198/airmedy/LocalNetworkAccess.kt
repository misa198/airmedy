package me.misa198.airmedy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

enum class LocalNetworkAccess { Granted, Required, Settings }

internal fun Context.hasLocalNetworkAccess(): Boolean =
    Build.VERSION.SDK_INT < 37 ||
        checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

internal fun localNetworkAccessState(
    granted: Boolean,
    requested: Boolean,
    showRationale: Boolean,
): LocalNetworkAccess = when {
    granted -> LocalNetworkAccess.Granted
    requested && !showRationale -> LocalNetworkAccess.Settings
    else -> LocalNetworkAccess.Required
}
