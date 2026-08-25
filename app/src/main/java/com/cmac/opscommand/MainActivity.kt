package com.cmac.opscommand

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cmac.opscommand.ui.DashboardScreen
import com.cmac.opscommand.ui.DashboardViewModel
import com.cmac.opscommand.ui.Page

/**
 * Single-activity host for the dashboard.
 *
 * TV-specific behaviour the browser build could not provide:
 *  - the screen never sleeps, so the board stays up 24/7 unattended
 *  - true fullscreen with system bars hidden (no Chrome UI, no clock overlay)
 *  - the D-pad drives the dashboard, since nothing on a TV has a mouse
 */
class MainActivity : ComponentActivity() {

    private val vm: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A dashboard above a register must not blank out or screensaver.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Immersive: no status bar, no navigation bar.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        setContent { DashboardScreen(vm) }
    }

    /**
     * Remote control mapping. Handled here rather than through Compose focus
     * because this dashboard has no focusable widgets — the whole screen is one
     * surface, and every key is a global command.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                vm.advance(+1); return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_PAGE_UP -> {
                vm.advance(-1); return true
            }

            // Hold/release the auto-rotation, mirroring the web LOCK button.
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                vm.toggleLock(); return true
            }

            // Page the assignment roster by hand.
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                vm.goTo(Page.JOBS); vm.nudgeJobsPage(+1); return true
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                vm.goTo(Page.JOBS); vm.nudgeJobsPage(-1); return true
            }

            // Jump straight to a board.
            KeyEvent.KEYCODE_1 -> { vm.goTo(Page.OVERVIEW); return true }
            KeyEvent.KEYCODE_2 -> { vm.goTo(Page.JOBS); return true }
            KeyEvent.KEYCODE_3 -> { vm.goTo(Page.COMMS); return true }
        }
        return super.onKeyDown(keyCode, event)
    }
}
