package com.example.myapplication.shared.domain.repository

import com.example.myapplication.shared.domain.model.BiometricCredentials
import com.example.myapplication.shared.domain.model.Session

interface AuthRepository {
    /** Session kept in memory/disk from a previous login, without touching the network. */
    fun cachedSession(): Session?

    /**
     * Re-validates a cached token against identity-service (GET /auth/me), refreshing
     * menu_permissions in case the role's grants changed since last login — same reason
     * identity-service's own `me` handler documents that field. Returns null (and clears the
     * cache) if there's no cached session, the token is no longer valid, or the role lost access.
     */
    suspend fun restoreSession(): Result<Session?>

    suspend fun login(email: String, password: String): Result<Session>

    /** true when a 401 caused this failure — the stored password is worth forgetting. */
    fun isInvalidCredentials(error: Throwable): Boolean

    fun logout()

    // Biometria: email/senha guardados cifrados no aparelho pra reenviar pro login normal depois
    // de uma digital confirmada — nao ha nada de biometria no backend, ele so ve email+senha de
    // novo a cada chamada. Sobrevivem a [logout] de proposito (mesmo padrao do app `mobile`).
    fun hasBiometricCredentials(): Boolean
    fun biometricCredentials(): BiometricCredentials?
    fun saveBiometricCredentials(email: String, password: String)
    fun clearBiometricCredentials()
}
