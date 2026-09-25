package com.example.myapplication.shared.domain.usecase

import com.example.myapplication.shared.domain.repository.AuthRepository

class ClearBiometricCredentialsUseCase(private val repository: AuthRepository) {
    operator fun invoke() = repository.clearBiometricCredentials()
}
