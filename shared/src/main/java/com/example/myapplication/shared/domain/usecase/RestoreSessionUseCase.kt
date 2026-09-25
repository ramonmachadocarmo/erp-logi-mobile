package com.example.myapplication.shared.domain.usecase

import com.example.myapplication.shared.domain.model.Session
import com.example.myapplication.shared.domain.repository.AuthRepository

class RestoreSessionUseCase(private val repository: AuthRepository) {
    /** Cached session first (instant), so callers can show something before the network round-trip resolves. */
    fun cached(): Session? = repository.cachedSession()

    suspend operator fun invoke(): Result<Session?> = repository.restoreSession()
}
