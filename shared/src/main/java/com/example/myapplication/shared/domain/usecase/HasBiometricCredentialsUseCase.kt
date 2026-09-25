package com.example.myapplication.shared.domain.usecase

import com.example.myapplication.shared.domain.repository.AuthRepository

class HasBiometricCredentialsUseCase(private val repository: AuthRepository) {
    operator fun invoke(): Boolean = repository.hasBiometricCredentials()
}
