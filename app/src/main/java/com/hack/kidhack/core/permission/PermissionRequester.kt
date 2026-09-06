package com.hack.kidhack.core.permission

import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Activity-scoped permission requester. Launchers are registered in [ComponentActivity]
 * construction (before STARTED), which is more reliable than Compose
 * [androidx.activity.compose.rememberLauncherForActivityResult] for location dialogs.
 */
class PermissionRequester(
    private val activity: ComponentActivity,
) {
    private var pendingMulti: ((Map<String, Boolean>) -> Unit)? = null
    private var pendingSingle: ((Boolean) -> Unit)? = null

    private val multiLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val callback = pendingMulti
        pendingMulti = null
        callback?.invoke(result)
    }

    private val singleLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val callback = pendingSingle
        pendingSingle = null
        callback?.invoke(granted)
    }

    fun request(
        vararg permissions: String,
        onResult: (Map<String, Boolean>) -> Unit,
    ) {
        val unique = permissions.filter { it.isNotBlank() }.distinct()
        if (unique.isEmpty()) {
            onResult(emptyMap())
            return
        }
        val needed = unique.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            onResult(unique.associateWith { true })
            return
        }
        KidhackPermissions.markAsked(activity, *needed.toTypedArray())
        pendingMulti = onResult
        multiLauncher.launch(needed.toTypedArray())
    }

    fun requestOne(
        permission: String,
        onResult: (Boolean) -> Unit,
    ) {
        if (permission.isBlank()) {
            onResult(false)
            return
        }
        if (ContextCompat.checkSelfPermission(activity, permission) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            onResult(true)
            return
        }
        KidhackPermissions.markAsked(activity, permission)
        pendingSingle = onResult
        singleLauncher.launch(permission)
    }
}
