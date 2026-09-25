package com.example.myapplication.shared.domain.usecase

import com.example.myapplication.shared.domain.repository.AuthRepository

class LogoutUseCase(
    private val repository: AuthRepository,
    // Runs after the session is dropped — used to wipe offline data so the next driver to log in
    // on this device can't be served the previous driver's cached route.
    private val onLoggedOut: () -> Unit = {},
) {
    operator fun invoke() {
        repository.logout()
        onLoggedOut()
    }
}
