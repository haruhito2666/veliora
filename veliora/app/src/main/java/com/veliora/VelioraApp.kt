package com.veliora

import android.app.Application
import android.content.Context
import android.util.Log
import com.clerk.api.Clerk
import com.veliora.data.repo.OrgStore
import com.veliora.data.repo.VelioraRepository

/**
 * Lightweight service locator (no DI framework).
 */
class VelioraApp : Application() {

    lateinit var repository: VelioraRepository
        private set

    lateinit var orgStore: OrgStore
        private set

    override fun onCreate() {
        super.onCreate()
        if (ClerkConfig.isConfigured()) {
            runCatching { Clerk.initialize(this, ClerkConfig.publishableKey) }
                .onFailure { Log.e("VelioraApp", "Clerk initialization failed", it) }
        }
        repository = VelioraRepository()
        orgStore = OrgStore(repository)
    }
}

val Context.app: VelioraApp
    get() = applicationContext as VelioraApp
