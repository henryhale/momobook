package com.momobook.security

import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.momobook.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optional app lock. Lives for the whole process, so a fresh start is always locked, and
 * coming back from the background re-locks once the chosen grace period has passed.
 */
object AppLock {
    /** Fingerprint/face, or the phone's PIN/pattern/password. Valid on every API level we support. */
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    private lateinit var prefs: Prefs
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /** When the app last went to the background, or null if it hasn't since the last check. */
    private var stoppedAt: Long? = null
    /** True while our own prompt is up; the PIN screen it opens must not count as leaving the app. */
    private var prompting = false
    /** Show the prompt by itself the next time the lock screen resumes. */
    private var autoPrompt = false

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = Prefs(context)
        _locked.value = prefs.appLock && canAuthenticate(context)
        autoPrompt = _locked.value
    }

    /** The phone has a screen lock (and maybe biometrics) we can ask for. */
    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** A fingerprint or face is enrolled. */
    fun hasBiometrics(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    fun onStop(changingConfigurations: Boolean) {
        if (!changingConfigurations && !prompting) stoppedAt = SystemClock.elapsedRealtime()
    }

    fun onStart(context: Context) {
        val since = stoppedAt ?: return
        stoppedAt = null
        // Never keep someone locked out after they removed their phone's screen lock.
        if (!prefs.appLock || !canAuthenticate(context)) { _locked.value = false; return }
        if (SystemClock.elapsedRealtime() - since >= prefs.lockGrace.millis) _locked.value = true
        if (_locked.value) autoPrompt = true
    }

    fun unlock() { _locked.value = false; autoPrompt = false }

    fun consumeAutoPrompt(): Boolean = autoPrompt.also { autoPrompt = false }

    sealed interface Result {
        data object Success : Result
        /** The user backed out; nothing to show. */
        data object Cancelled : Result
        data class Error(val code: Int, val message: String) : Result
    }

    fun authenticate(activity: FragmentActivity, title: String, onResult: (Result) -> Unit) {
        if (prompting) return
        prompting = true
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                prompting = false
                onResult(Result.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                prompting = false
                onResult(
                    when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> Result.Cancelled
                        else -> Result.Error(errorCode, errString.toString())
                    }
                )
            }
            // onAuthenticationFailed = one bad scan; the prompt stays up and lets them retry.
        })
        val subtitle = if (hasBiometrics(activity)) "Use your fingerprint or screen lock" else "Use your screen lock"
        runCatching {
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setAllowedAuthenticators(AUTHENTICATORS)
                    .build()
            )
        }.onFailure {
            prompting = false
            onResult(Result.Error(-1, it.message ?: "Couldn't open the unlock prompt"))
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
