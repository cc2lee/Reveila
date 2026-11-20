package com.reveila.aegin.ui.mascot

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Android `SYSTEM_ALERT_WINDOW` floating mascot host that wires [LifecycleOwner] and
 * [SavedStateRegistryOwner] directly to the overlay [ComposeView] so `rememberInfiniteTransition`
 * animates continuously even when floating over other apps.
 */
class AndroidOverlayMascotHost(
    private val context: Context,
    private val controller: AeginMascotController,
    private val onMascotTapped: () -> Unit
) : LifecycleOwner, SavedStateRegistryOwner {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private var overlayView: ComposeView? = null

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun show() {
        if (overlayView != null) return

        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 180
        }

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@AndroidOverlayMascotHost)
            setViewTreeSavedStateRegistryOwner(this@AndroidOverlayMascotHost)
            setContent {
                val state by controller.state.collectAsState()
                AeginRobotDogMascot(
                    state = state,
                    mode = AeginMascotMode.COMPANION,
                    size = 64.dp,
                    modifier = Modifier.clickable { onMascotTapped() }
                )
            }
        }

        windowManager.addView(composeView, params)
        overlayView = composeView
    }

    fun dismiss() {
        overlayView?.let { view ->
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            windowManager.removeView(view)
            overlayView = null
        }
    }
}
