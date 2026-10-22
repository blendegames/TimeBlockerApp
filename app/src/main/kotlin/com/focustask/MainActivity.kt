package com.focustask

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focustask.ui.AppRoot
import com.focustask.ui.theme.FocusTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshAlerts() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        lifecycleScope.launch {
            // Permission or ringer changes happen outside the app: re-plan when we come back.
            repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.refreshAlerts() }
        }
        setContent {
            val ui by vm.ui.collectAsStateWithLifecycle()
            FocusTheme(wallpaper = ui?.data?.settings?.wallpaperColors == true) { AppRoot(vm) }
        }
    }

    override fun onStop() {
        super.onStop()
        // the copy is written a moment after each change: make sure the last change is in it before the app goes away
        (application as FocusApp).let { app -> app.scope.launch { runCatching { app.mirror.saveNow() } } }
    }
}
