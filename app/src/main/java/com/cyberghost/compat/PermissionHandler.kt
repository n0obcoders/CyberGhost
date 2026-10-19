package com.cyberghost.compat

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

class PermissionHandler(activity: ComponentActivity) {
    private var onResult: ((Boolean) -> Unit)? = null
    private val launcher: ActivityResultLauncher<Array<String>> = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> onResult?.invoke(r.values.all { it }) }
    fun requestAll(onComplete: (Boolean) -> Unit) { onResult = onComplete; launcher.launch(VersionCompat.requiredPermissions()) }
}
