package com.example.myapplication.shared.domain.usecase

import com.example.myapplication.shared.domain.repository.AuthRepository

class SaveBiometricCredentialsUseCase(private val repository: AuthRepository) {
    operator fun invoke(email: String, password: String) = repository.saveBiometricCredentials(email, password)
}
