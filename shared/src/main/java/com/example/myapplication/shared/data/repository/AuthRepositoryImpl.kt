package com.example.myapplication.shared.data.repository

import com.example.myapplication.shared.data.remote.ApiClient
import com.example.myapplication.shared.data.remote.ApiException
import com.example.myapplication.shared.data.session.TokenStore
import com.example.myapplication.shared.domain.model.BiometricCredentials
import com.example.myapplication.shared.domain.model.Session
import com.example.myapplication.shared.domain.repository.AuthRepository
import org.json.JSONObject

/**
 * Same endpoint identity-service exposes to web/mobile: POST /auth/login returns
 * {token, refresh_token, expires_in, user{...,role_code}, menu_permissions{key:level}} — see
 * services/identity-service/internal/adapters/http/handler.go's sessionResponse/me.
 */
class AuthRepositoryImpl(
    private val api: ApiClient,
    private val tokenStore: TokenStore,
) : AuthRepository {
    private var cached: Session? = tokenStore.read()?.let { raw ->
        runCatching { sessionFrom(JSONObject(raw)) }.getOrNull()
    }

    override fun cachedSession(): Session? = cached

    override suspend fun restoreSession(): Result<Session?> = runCatching {
        val existing = cached ?: return@runCatching null
        val me = api.get("/api/identity/auth/me")
        val refreshed = sessionFrom(
            JSONObject()
                .put("token", existing.token)
                .put(
                    "user",
                    JSONObject()
                        .put("id", me.optString("id"))
                        .put("name", me.optString("name"))
                        .put("role_code", me.optString("role_code")),
                )
                .put("menu_permissions", me.optJSONObject("menu_permissions") ?: JSONObject()),
        )
        persist(refreshed)
        refreshed
    }.recoverCatching { err ->
        // An expired/invalid token (401) means "no session" here, not a hard failure — everything
        // else (network down, 5xx) should surface as an error instead of silently logging out.
        if (err is ApiException && err.statusCode == 401) {
            logout()
            null
        } else {
            throw err
        }
    }

    // Whether the role can actually use THIS app (Rotas + Entrega) is an authorization concern,
    // not an authentication one — checked by RouteFlowController after a successful login/restore
    // (RouteUiState.NoAccess), so a wrong-password error and a "right password, no permission"
    // one read differently to the driver.
    override suspend fun login(email: String, password: String): Result<Session> = runCatching {
        val body = JSONObject().put("email", email).put("password", password)
        val res = api.post("/api/identity/auth/login", body)
        val session = sessionFrom(res)
        persist(session)
        session
    }

    override fun isInvalidCredentials(error: Throwable): Boolean =
        error is ApiException && error.statusCode == 401

    override fun logout() {
        tokenStore.clear()
        cached = null
    }

    override fun hasBiometricCredentials(): Boolean = tokenStore.hasBiometricCredentials()

    override fun biometricCredentials(): BiometricCredentials? =
        tokenStore.readBiometricCredentials()?.let { (email, password) -> BiometricCredentials(email, password) }

    override fun saveBiometricCredentials(email: String, password: String) =
        tokenStore.saveBiometricCredentials(email, password)

    override fun clearBiometricCredentials() = tokenStore.clearBiometricCredentials()

    private fun persist(session: Session) {
        val json = JSONObject()
            .put("token", session.token)
            .put(
                "user",
                JSONObject()
                    .put("id", session.userId)
                    .put("name", session.userName)
                    .put("role_code", session.roleCode),
            )
            .put("menu_permissions", JSONObject(session.menuPermissions))
        tokenStore.save(json.toString())
        cached = session
    }

    private fun sessionFrom(json: JSONObject): Session {
        val user = json.optJSONObject("user") ?: JSONObject()
        val perms = mutableMapOf<String, Int>()
        json.optJSONObject("menu_permissions")?.let { obj ->
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                perms[key] = obj.optInt(key)
            }
        }
        return Session(
            token = json.optString("token"),
            userId = user.optString("id"),
            userName = user.optString("name"),
            roleCode = user.optString("role_code"),
            menuPermissions = perms,
        )
    }
}
