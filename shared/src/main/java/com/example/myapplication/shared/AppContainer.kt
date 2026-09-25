package com.example.myapplication.shared

import android.content.Context
import com.example.myapplication.shared.data.biometric.BiometricService
import com.example.myapplication.shared.data.cache.FileOfflineCache
import com.example.myapplication.shared.data.cache.FilePendingActionQueue
import com.example.myapplication.shared.data.remote.ApiClient
import com.example.myapplication.shared.data.repository.AuthRepositoryImpl
import com.example.myapplication.shared.data.repository.CachedConfigRepository
import com.example.myapplication.shared.data.repository.CachedRouteRepository
import com.example.myapplication.shared.data.repository.ConfigRepositoryImpl
import com.example.myapplication.shared.data.repository.RouteRepositoryImpl
import com.example.myapplication.shared.data.session.TokenStore
import com.example.myapplication.shared.data.sync.PendingActionSyncer
import com.example.myapplication.shared.data.sync.SyncScheduler
import com.example.myapplication.shared.data.sync.WorkManagerSyncScheduler
import com.example.myapplication.shared.domain.repository.AuthRepository
import com.example.myapplication.shared.domain.repository.ConfigRepository
import com.example.myapplication.shared.domain.repository.RouteRepository
import com.example.myapplication.shared.domain.usecase.BiometricCredentialsUseCase
import com.example.myapplication.shared.domain.usecase.ClearBiometricCredentialsUseCase
import com.example.myapplication.shared.domain.usecase.ConfirmArrivalUseCase
import com.example.myapplication.shared.domain.usecase.FailDeliveryUseCase
import com.example.myapplication.shared.domain.usecase.HasBiometricCredentialsUseCase
import com.example.myapplication.shared.domain.usecase.ListVehiclesUseCase
import com.example.myapplication.shared.domain.usecase.LoadTodaysRouteUseCase
import com.example.myapplication.shared.domain.usecase.LoginUseCase
import com.example.myapplication.shared.domain.usecase.LogoutUseCase
import com.example.myapplication.shared.domain.usecase.RefreshRouteUseCase
import com.example.myapplication.shared.domain.usecase.RestoreSessionUseCase
import com.example.myapplication.shared.domain.usecase.SaveBiometricCredentialsUseCase
import com.example.myapplication.shared.domain.usecase.SelectRouteOptionUseCase
import java.io.File

/**
 * Manual composition root — shared by MainActivity (:mobile) and MyCarAppScreen (:automotive via
 * :shared), so login/session/route wiring exists in exactly one place instead of being built
 * twice like the old mock (both had their own `RouteRepositoryImpl(FileSystemRouteDataSource(...))`).
 */
class AppContainer(context: Context) {
    private val tokenStore = TokenStore(context.applicationContext)

    // Offline copy of vehicles + today's plan; wiped on logout/401 so it never outlives the session.
    private val offlineCache = FileOfflineCache(File(context.applicationContext.filesDir, "offline"))

    // Deliveries confirmed offline. Deliberately NOT under offlineCache's dir and NOT cleared on
    // logout/401: they are the driver's work, and dropping them would lose real deliveries. They
    // are sent with whichever session is active when connectivity returns.
    val pendingActionQueue = FilePendingActionQueue(File(context.applicationContext.filesDir, "pending/actions.json"))
    private val syncScheduler: SyncScheduler = WorkManagerSyncScheduler(context.applicationContext)

    // ApiClient needs a token/401 callback into AuthRepository, but AuthRepository needs an
    // ApiClient to call identity-service — broken by having ApiClient close over this `lateinit`
    // instead of AuthRepository directly; the lambdas only run later (per request), by which
    // point [authRepository] below has backfilled it.
    private lateinit var authRepositoryRef: AuthRepository

    private val apiClient: ApiClient = ApiClient(
        tokenProvider = { authRepositoryRef.cachedSession()?.token },
        onUnauthorized = {
            authRepositoryRef.logout()
            offlineCache.clear()
        },
    )

    val authRepository: AuthRepository = AuthRepositoryImpl(apiClient, tokenStore).also { authRepositoryRef = it }

    // RouteRepositoryImpl gets the *uncached* config repo: customer names must come live (or fail
    // over to the cached plan, which already carries them), never from a stale copy.
    private val remoteConfigRepository = ConfigRepositoryImpl(apiClient)
    private val configRepository: ConfigRepository = CachedConfigRepository(remoteConfigRepository, offlineCache)
    private val remoteRouteRepository = RouteRepositoryImpl(apiClient, remoteConfigRepository)
    private val routeRepository: RouteRepository = CachedRouteRepository(
        delegate = remoteRouteRepository,
        cache = offlineCache,
        queue = pendingActionQueue,
        onQueued = syncScheduler::schedule,
    )

    /** Used by the background worker; talks to the server directly (a failure must not re-queue). */
    val pendingActionSyncer = PendingActionSyncer(pendingActionQueue, remoteRouteRepository)

    // BiometricPrompt precisa de uma FragmentActivity viva por chamada — por isso o service fica
    // sem estado aqui (so metodos), a Activity de verdade vem de quem chama (so a tela do celular).
    val biometricService = BiometricService()

    val restoreSessionUseCase = RestoreSessionUseCase(authRepository)
    val loginUseCase = LoginUseCase(authRepository)
    val logoutUseCase = LogoutUseCase(authRepository, onLoggedOut = offlineCache::clear)
    val hasBiometricCredentialsUseCase = HasBiometricCredentialsUseCase(authRepository)
    val biometricCredentialsUseCase = BiometricCredentialsUseCase(authRepository)
    val saveBiometricCredentialsUseCase = SaveBiometricCredentialsUseCase(authRepository)
    val clearBiometricCredentialsUseCase = ClearBiometricCredentialsUseCase(authRepository)
    val listVehiclesUseCase = ListVehiclesUseCase(configRepository)
    val loadTodaysRouteUseCase = LoadTodaysRouteUseCase(routeRepository)
    val refreshRouteUseCase = RefreshRouteUseCase(routeRepository)
    val selectRouteOptionUseCase = SelectRouteOptionUseCase(routeRepository)
    val confirmArrivalUseCase = ConfirmArrivalUseCase(routeRepository)
    val failDeliveryUseCase = FailDeliveryUseCase(routeRepository)

    init {
        // Anything left from a previous run (process killed, worker gave up) gets another chance.
        if (pendingActionQueue.pending().isNotEmpty()) syncScheduler.schedule()
    }
}
