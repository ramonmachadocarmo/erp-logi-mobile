package com.example.myapplication.shared.data.remote

import android.os.Build
import com.example.myapplication.shared.BuildConfig

/**
 * Base URL for the gateway-service (GATEWAY_HTTP_PORT, same one the web app and the Flutter
 * `mobile` app talk to). Mirrors mobile/lib/core/config/api_config.dart's convention:
 * 10.0.2.2 is the emulator's alias for the host machine's localhost.
 *
 * A real device on the depot's Wi-Fi needs the gateway's LAN IP — override it with
 * [ApiConfig.override] (e.g. from a settings screen) instead of hardcoding one, since the depot's
 * LAN IP isn't knowable at build time.
 */
object ApiConfig {
    private const val EMULATOR_BASE_URL = "http://10.0.2.2:8086"
    private const val DEFAULT_LAN_BASE_URL = "http://localhost:8086"

    @Volatile
    private var overrideUrl: String? = null

    // Fixed at build time via -PapiBaseUrl (release/CI); empty in a plain debug build.
    private val buildUrl: String = BuildConfig.API_BASE_URL.trim().trimEnd('/')

    val baseUrl: String
        get() = overrideUrl ?: buildUrl.takeIf { it.isNotEmpty() } ?: run {
            if (isEmulator()) EMULATOR_BASE_URL else DEFAULT_LAN_BASE_URL
        }

    fun override(url: String?) {
        overrideUrl = url?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
    }

    private fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT ?: ""
        val model = Build.MODEL ?: ""
        val manufacturer = Build.MANUFACTURER ?: ""
        val brand = Build.BRAND ?: ""
        val product = Build.PRODUCT ?: ""
        return fingerprint.startsWith("generic") ||
            fingerprint.startsWith("unknown") ||
            model.contains("google_sdk") ||
            model.contains("Emulator") ||
            model.contains("Android SDK built for x86") ||
            manufacturer.contains("Genymotion") ||
            (brand.startsWith("generic") && product.startsWith("sdk")) ||
            product == "google_sdk"
    }
}
