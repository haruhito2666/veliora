package com.veliora

import android.app.Application
import android.content.Context
import android.util.Log
import com.clerk.api.Clerk
import com.veliora.data.repo.OrgStore
import com.veliora.data.repo.VelioraRepository
import com.veliora.data.sofuu.EmbeddedSofuu

/**
 * Lightweight service locator (no DI framework).
 */
class VelioraApp : Application() {

    lateinit var sofuu: EmbeddedSofuu
        private set

    lateinit var repository: VelioraRepository
        private set

    lateinit var orgStore: OrgStore
        private set

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        if (ClerkConfig.isConfigured()) {
            runCatching { Clerk.initialize(this, ClerkConfig.publishableKey) }
                .onFailure { Log.e("VelioraApp", "Clerk initialization failed", it) }
        }

        // The Veliora gateway now runs inside libsofuu on this device. There
        // is no Veliora server process and no `sofuu` executable in the APK.
        sofuu = EmbeddedSofuu(this).also { it.start() }
        repository = VelioraRepository(sofuu)
        orgStore = OrgStore(repository)
    }
}

val Context.app: VelioraApp
    get() = applicationContext as VelioraApp
