package com.example.myapplication.shared.domain.usecase

import com.example.myapplication.shared.domain.model.BiometricCredentials
import com.example.myapplication.shared.domain.model.Session
import com.example.myapplication.shared.domain.repository.AuthRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class LogoutUseCaseTest {
    @Test
    fun `drops the session first, then runs the cleanup hook`() {
        val calls = mutableListOf<String>()
        val repo = object : AuthRepository {
            override fun cachedSession(): Session? = null
            override suspend fun restoreSession(): Result<Session?> = error("unused")
            override suspend fun login(email: String, password: String): Result<Session> = error("unused")
            override fun isInvalidCredentials(error: Throwable): Boolean = TODO("unused")
            override fun logout() {
                calls += "logout"
            }
            override fun hasBiometricCredentials(): Boolean = error("unused")
            override fun biometricCredentials(): BiometricCredentials? = error("unused")
            override fun saveBiometricCredentials(email: String, password: String) = error("unused")
            override fun clearBiometricCredentials() = error("unused")
        }
        LogoutUseCase(repo, onLoggedOut = { calls += "hook" })()
        assertEquals(listOf("logout", "hook"), calls)
    }
}
