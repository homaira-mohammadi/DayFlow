package com.dayflow.security

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLockMode(val label: String) {
    OFF("Off"),
    PRIVATE("Journal & Cycle"),
    WHOLE_APP("Whole app"),
}

/**
 * Unlock state for the optional app lock. Uses the phone's own fingerprint, face or screen-lock
 * PIN, so DayFlow never stores a password. Locks again after the app has been in the background
 * for [RELOCK_AFTER_MS].
 */
class AppLock {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()
    private var backgroundAt: Long? = null

    fun onBackground() {
        backgroundAt = System.currentTimeMillis()
    }

    fun onForeground() {
        val since = backgroundAt ?: return
        if (System.currentTimeMillis() - since > RELOCK_AFTER_MS) _unlocked.value = false
        backgroundAt = null
    }

    fun markUnlocked() {
        _unlocked.value = true
    }

    companion object {
        /** Short enough to protect a phone left on a table; long enough not to re-ask after using the photo picker. */
        const val RELOCK_AFTER_MS = 30_000L
        private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

        /** True when the phone has a fingerprint, face or screen lock that can be used. */
        fun isAvailable(context: Context): Boolean =
            BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

        fun authenticate(
            activity: FragmentActivity,
            title: String,
            onSuccess: () -> Unit,
            onFailure: (String) -> Unit = {},
        ) {
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailure(errString.toString())
                },
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle("Use your fingerprint, face or phone PIN")
                    .setAllowedAuthenticators(AUTHENTICATORS)
                    .build(),
            )
        }
    }
}

fun Context.findFragmentActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}
