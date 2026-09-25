package com.example.myapplication.shared.domain.usecase

import com.example.myapplication.shared.domain.model.BiometricCredentials
import com.example.myapplication.shared.domain.repository.AuthRepository

class BiometricCredentialsUseCase(private val repository: AuthRepository) {
    operator fun invoke(): BiometricCredentials? = repository.biometricCredentials()
}
