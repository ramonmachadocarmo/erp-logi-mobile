package com.example.myapplication.shared.domain.repository

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

    fun logout()
}
