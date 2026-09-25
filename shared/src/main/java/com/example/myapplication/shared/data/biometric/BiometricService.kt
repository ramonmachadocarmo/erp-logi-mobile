package com.example.myapplication.shared.data.biometric

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Thin wrapper over androidx.biometric, mirroring mobile/lib/core/biometric/biometric_service.dart
 * (`local_auth`) — same two-method surface, so the login screen logic reads the same in both apps.
 * A `BiometricPrompt` needs a live [FragmentActivity] to host it (not just an app Context), which
 * is why [authenticate] takes one per call instead of this being built once in AppContainer.
 */
class BiometricService {
    /** True only with real biometric hardware AND at least one fingerprint/face enrolled — a
     *  device with just a screen-lock PIN does not count (BIOMETRIC_STRONG, no device-credential
     *  fallback), matching local_auth's `biometricOnly: true` on the Flutter side. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /** Cancelling, failing or any platform error resolves to false — same contract as
     *  BiometricService.authenticate on the Flutter side. */
    suspend fun authenticate(activity: FragmentActivity, reason: String): Boolean =
        suspendCancellableCoroutine { cont ->
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        // Includes the user tapping "Cancelar" — a deliberate no, not an error to surface.
                        if (cont.isActive) cont.resume(false)
                    }

                    // onAuthenticationFailed (one wrong finger) is NOT terminal — the prompt stays open
                    // for another attempt, so nothing resolves here.
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Confirme sua identidade")
                .setSubtitle(reason)
                .setNegativeButtonText("Cancelar")
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .build()
            prompt.authenticate(info)
            cont.invokeOnCancellation { prompt.cancelAuthentication() }
        }
}
