package com.dayflow

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.dayflow.data.prefs.UserSettings
import com.dayflow.security.AppLockMode
import com.dayflow.ui.components.LockGate
import com.dayflow.ui.navigation.DayFlowNavHost
import com.dayflow.ui.onboarding.OnboardingScreen
import com.dayflow.ui.onboarding.TourScreen
import com.dayflow.ui.theme.DayFlowTheme
import kotlinx.coroutines.launch

/** A FragmentActivity because the system unlock prompt (BiometricPrompt) needs one. */
class MainActivity : FragmentActivity() {

    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DayFlowApp).container
        setContent {
            // Null until settings are read, so the welcome screen never flashes for returning users.
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val s = settings
            // While the app lock is on, hide content from the recent-apps preview and screenshots.
            LaunchedEffect(s?.appLock) {
                if (s != null && s.appLock != AppLockMode.OFF) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            DayFlowTheme(s ?: UserSettings()) {
                when {
                    s == null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    !s.onboarded -> OnboardingScreen { r ->
                        lifecycleScope.launch {
                            container.settings.update {
                                it.copy(
                                    name = r.name,
                                    gender = r.gender,
                                    cycleTracking = r.periodTracking,
                                    prayerTracking = r.prayerTracking,
                                    onboarded = true,
                                )
                            }
                            container.reminders.refreshAll()
                        }
                    }
                    !s.tourSeen -> TourScreen(prayers = s.prayerTracking, cycle = s.cycleTracking) {
                        lifecycleScope.launch { container.settings.update { it.copy(tourSeen = true) } }
                    }
                    else -> LockGate(required = s.appLock == AppLockMode.WHOLE_APP, cover = true) {
                        DayFlowNavHost(cycleEnabled = s.cycleTracking, lockPrivate = s.appLock == AppLockMode.PRIVATE)
                    }
                }
            }
        }
        if (savedInstanceState == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        (application as DayFlowApp).container.appLock.onForeground()
    }

    override fun onStop() {
        super.onStop()
        (application as DayFlowApp).container.appLock.onBackground()
    }
}
