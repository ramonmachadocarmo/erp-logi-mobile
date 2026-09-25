package com.example.myapplication.shared

import android.content.Intent
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.validation.HostValidator
import com.example.myapplication.shared.presentation.map.CustomMapSurfaceRenderer

class MyCarAppService : CarAppService() {

    // ALLOW_ALL accepts a bind from ANY app claiming to be a car host — fine for local testing
    // (adb / Desktop Head Unit aren't signed by the real hosts), but in a release build it would
    // let an untrusted app drive this Screen's callbacks (confirmArrival, login state, customer
    // addresses). `hosts_allowlist_sample` is androidx.car.app's own shipped list of the real
    // Android Auto / Android Automotive OS host package names + signing certificate SHA-256
    // digests — only those may bind once this is a real (non-debug) build.
    override fun createHostValidator(): HostValidator {
        if (BuildConfig.DEBUG) return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        return HostValidator.Builder(applicationContext)
            .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
            .build()
    }

    override fun onCreateSession(): Session {
        return object : Session() {
            private val surfaceRenderer = CustomMapSurfaceRenderer()
            private var activeSurface: Surface? = null

            override fun onCreateScreen(intent: Intent): Screen {
                val screen = MyCarAppScreen(carContext)

                carContext.getCarService(AppManager::class.java)
                    .setSurfaceCallback(object : SurfaceCallback {
                        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
                            activeSurface = surfaceContainer.surface
                            activeSurface?.let { surface ->
                                surfaceRenderer.renderRouteOnSurface(surface, screen.currentPlan())
                            }
                        }

                        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
                            activeSurface = null
                        }
                    })

                screen.setOnRouteUpdatedListener { plan ->
                    activeSurface?.let { surface ->
                        surfaceRenderer.renderRouteOnSurface(surface, plan)
                    }
                }

                return screen
            }
        }
    }
}
