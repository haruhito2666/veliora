package com.veliora

import android.app.Application
import android.content.Context
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
        repository = VelioraRepository()
        orgStore = OrgStore(repository)
    }
}

val Context.app: VelioraApp
    get() = applicationContext as VelioraApp
