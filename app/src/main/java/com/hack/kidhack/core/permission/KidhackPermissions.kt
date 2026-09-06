package com.hack.kidhack.core.permission

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.hack.kidhack.background.accessibility.HarmfulBlockAccessibilityService

object KidhackPermissions {
    val locationFine = Manifest.permission.ACCESS_FINE_LOCATION
    val locationCoarse = Manifest.permission.ACCESS_COARSE_LOCATION

    val location = arrayOf(locationFine, locationCoarse)

    val camera = Manifest.permission.CAMERA

    val backgroundLocation: String?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        } else {
            null
        }

    val notifications: String?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Permissions to pass to [ActivityResultContracts.RequestMultiplePermissions] for location.
     *
     * On Android 12+ (API 31), requesting [locationFine] alone is ignored by the system
     * ("No requestable permission in the request"). Request coarse+fine together when
     * neither is granted; request fine alone only to upgrade from approximate.
     */
    fun locationPermissionsToRequest(context: Context): Array<String> {
        if (isGranted(context, locationFine)) return emptyArray()
        return if (isGranted(context, locationCoarse)) {
            arrayOf(locationFine)
        } else {
            arrayOf(locationCoarse, locationFine)
        }
    }

    fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    /**
     * Android stops showing the system dialog after repeated denies.
     * First ask, or after a soft deny (rationale == true) → dialog can still show.
     * After hard deny → only Settings can change it.
     */
    fun canShowSystemDialog(activity: Activity, permission: String): Boolean {
        if (isGranted(activity, permission)) return false
        if (!wasAsked(activity, permission)) return true
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    fun canShowSystemDialogForAny(activity: Activity, vararg permissions: String): Boolean =
        permissions.any { canShowSystemDialog(activity, it) }

    fun markAsked(context: Context, vararg permissions: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().apply {
            permissions.filter { it.isNotBlank() }.forEach { putBoolean(askedKey(it), true) }
            apply()
        }
    }

    fun recordDeny(context: Context, vararg permissions: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updates = permissions.filter { it.isNotBlank() }.associateWith { permission ->
            prefs.getInt(denyCountKey(permission), 0) + 1
        }
        prefs.edit().apply {
            updates.forEach { (permission, count) -> putInt(denyCountKey(permission), count) }
            apply()
        }
    }

    fun clearDenyHistory(context: Context, vararg permissions: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().apply {
            permissions.filter { it.isNotBlank() }.forEach { permission ->
                remove(denyCountKey(permission))
                remove(askedKey(permission))
            }
            apply()
        }
    }

    fun denyCount(context: Context, permission: String): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(denyCountKey(permission), 0)

    /**
     * After 2+ denies (or OS hard-block), the system dialog will not appear —
     * 「허용하기」 should open app settings instead.
     */
    fun shouldOpenSettingsForPermission(activity: Activity, permission: String): Boolean {
        if (isGranted(activity, permission)) return false
        if (denyCount(activity, permission) >= 2) return true
        if (!wasAsked(activity, permission)) return false
        return !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    fun shouldOpenSettingsForAny(activity: Activity, vararg permissions: String): Boolean =
        permissions.any { shouldOpenSettingsForPermission(activity, it) }

    private fun wasAsked(context: Context, permission: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(askedKey(permission), false)

    private fun askedKey(permission: String) = "asked_$permission"
    private fun denyCountKey(permission: String) = "deny_count_$permission"

    fun appDetailsSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )

    /** Precise location for child tracking. */
    fun hasLocation(context: Context): Boolean = isGranted(context, locationFine)

    fun hasBackgroundLocation(context: Context): Boolean {
        val permission = backgroundLocation ?: return true
        return isGranted(context, permission)
    }

    fun hasNotifications(context: Context): Boolean {
        val permission = notifications ?: return true
        return isGranted(context, permission)
    }

    fun hasCamera(context: Context): Boolean = isGranted(context, camera)

    fun hasUsageStats(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun hasAccessibility(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val component = ComponentName(context, HarmfulBlockAccessibilityService::class.java)
        val flat = component.flattenToString()
        return enabled.split(':').any { it.equals(flat, ignoreCase = true) }
    }

    /**
     * MediaProjection is not a normal runtime permission. We record that the child
     * completed the system screen-capture consent dialog during onboarding.
     */
    fun hasScreenCaptureConsent(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SCREEN_CAPTURE_CONSENT, false)

    fun markScreenCaptureConsent(context: Context, granted: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SCREEN_CAPTURE_CONSENT, granted)
            .apply()
    }

    fun hasAllRequiredForChildControl(context: Context): Boolean =
        hasLocation(context) &&
            hasBackgroundLocation(context) &&
            hasNotifications(context) &&
            hasUsageStats(context) &&
            hasOverlay(context) &&
            hasAccessibility(context) &&
            hasScreenCaptureConsent(context)

    private const val PREFS = "kidhack_permission_requests"
    private const val KEY_SCREEN_CAPTURE_CONSENT = "screen_capture_consent"
}
