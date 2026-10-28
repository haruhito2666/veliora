package com.veliora.data.repo

import com.veliora.data.Investigation
import com.veliora.data.Organization
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Central store for organization data.
 *
 * The old implementation subscribed to server-sent events. The embedded
 * runtime has no background HTTP pump, so this store polls the in-process
 * gateway and advances its event loop between reads.
 */
class OrgStore(
    private val repository: VelioraRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _organization = MutableStateFlow<Organization?>(null)
    val organization: StateFlow<Organization?> = _organization.asStateFlow()

    private val _investigations = MutableStateFlow<List<Investigation>>(emptyList())
    val investigations: StateFlow<List<Investigation>> = _investigations.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    @Volatile private var currentOrgId: String? = null
    private var pollJob: Job? = null

    /** Loads the organization snapshot, then keeps it fresh locally. */
    fun loadOrg(orgId: String) {
        currentOrgId = orgId
        pollJob?.cancel()

        scope.launch {
            _loading.value = true
            _error.value = null
            try {
                val snap = repository.organization(orgId)
                _organization.value = Mapper.toOrganization(snap, snap.dna)
                _investigations.value = repository.investigations(orgId).map { Mapper.toInvestigation(it) }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load organization"
            } finally {
                _loading.value = false
            }
        }

        pollJob = scope.launch { pollLoop(orgId) }
    }

    private suspend fun pollLoop(orgId: String) {
        while (currentCoroutineContext().isActive && currentOrgId == orgId) {
            val active = _investigations.value.any { it.phase != com.veliora.data.InvestigationPhase.REPORT }
            delay(if (active) 250 else 2_000)
            if (currentOrgId != orgId) break

            // Drain one bounded slice of the runtime loop so scripted or
            // local-model investigation steps can progress while the UI is
            // idle. Idle organizations only need an occasional refresh.
            if (active) runCatching { repository.pump() }

            runCatching { repository.organization(orgId) }
                .onSuccess { snap ->
                    _organization.value = Mapper.toOrganization(snap, snap.dna)
                    _error.value = null
                }

            runCatching { repository.investigations(orgId) }
                .onSuccess { list ->
                    _investigations.value = list.map { Mapper.toInvestigation(it) }
                    _error.value = null
                }
        }
    }

    /** Creates an investigation and returns its id. */
    suspend fun createInvestigation(goal: String, depth: String = "standard"): String? {
        val orgId = currentOrgId ?: return null
        return runCatching {
            repository.createInvestigation(orgId, goal, depth).investigationId
        }.getOrNull()
    }

    fun clear() {
        pollJob?.cancel()
        pollJob = null
        currentOrgId = null
        _organization.value = null
        _investigations.value = emptyList()
        _error.value = null
    }
}
