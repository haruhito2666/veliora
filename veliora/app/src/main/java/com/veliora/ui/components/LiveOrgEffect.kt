package com.veliora.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.veliora.data.repo.OrgStore

/**
 * Attaches [store] to this screen for as long as the screen is started.
 *
 * The embedded gateway has no background loop of its own: polling and the
 * bounded libuv pump must stop when the app is backgrounded or no screen is
 * watching the organization. Multiple screens can attach at once; the store
 * keeps one shared poll and stops it when the last one detaches.
 */
@Composable
fun LiveOrgEffect(
    orgId: String,
    store: OrgStore,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(orgId, store, lifecycleOwner) {
        var attached = false

        fun attach() {
            if (attached) return
            store.attach(orgId)
            attached = true
        }

        fun detach() {
            if (!attached) return
            store.detach(orgId)
            attached = false
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> attach()
                Lifecycle.Event.ON_STOP -> detach()
                Lifecycle.Event.ON_DESTROY -> detach()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            attach()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            detach()
        }
    }
}
