package com.smartcommunity.sos.config

import android.os.Build
import com.smartcommunity.sos.BuildConfig

object ApiConfig {
    val baseUrl: String by lazy {
        val configured = BuildConfig.API_BASE_URL.trim().trimEnd('/')
        if (configured.isNotBlank() && !configured.equals("auto", ignoreCase = true)) {
            configured
        } else {
            if (isEmulator()) {
                "http://10.0.2.2:8001"
            } else {
                "http://127.0.0.1:8001"
            }
        }
    }

    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
            Build.FINGERPRINT.contains("unknown", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("Android SDK built for", ignoreCase = true) ||
            Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
            Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
            Build.PRODUCT.contains("sdk", ignoreCase = true) ||
            Build.PRODUCT.contains("emulator", ignoreCase = true)
    }
}
