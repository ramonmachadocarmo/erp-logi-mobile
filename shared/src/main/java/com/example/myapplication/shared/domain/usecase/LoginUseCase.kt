package com.example.myapplication.shared.domain.usecase

import com.example.myapplication.shared.domain.model.Session
import com.example.myapplication.shared.domain.repository.AuthRepository

class LoginUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): Result<Session> =
        repository.login(email, password)
}
