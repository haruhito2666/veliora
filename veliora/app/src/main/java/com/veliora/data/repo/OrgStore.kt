package com.veliora.data.repo

import com.veliora.data.Investigation
import com.veliora.data.Organization
import com.veliora.data.realtime.EventFrame
import com.veliora.data.realtime.RealtimeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Central store for organization data and live SSE events. */
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

    @Volatile private var realtime: RealtimeClient? = null
    @Volatile private var currentOrgId: String? = null

    /** Loads the organization snapshot and investigations, then opens the SSE stream. */
    fun loadOrg(orgId: String) {
        currentOrgId = orgId
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
        connectRealtime(orgId)
    }

    private fun connectRealtime(orgId: String) {
        realtime?.close()
        val client = RealtimeClient(orgId)
        realtime = client
        scope.launch { client.events.collect { frame -> applyFrame(frame) } }
        client.connect()
    }

    private fun applyFrame(frame: EventFrame) {
        when (frame.event) {
            "investigation.started", "investigation.phase_changed", "investigation.completed" ->
                refreshInvestigations()
            "worker.status_changed",
            "evidence.submitted", "evidence.challenged", "evidence.upheld",
            "communication.sent", "communication.received",
            "evolution.proposed", "evolution.executed",
            "meeting.called", "meeting.started", "meeting.concluded" ->
                refreshOrganization()
        }
    }

    private fun refreshInvestigations() {
        val orgId = currentOrgId ?: return
        scope.launch {
            runCatching { repository.investigations(orgId).map { Mapper.toInvestigation(it) } }
                .onSuccess { _investigations.value = it }
        }
    }

    private fun refreshOrganization() {
        val orgId = currentOrgId ?: return
        scope.launch {
            runCatching { repository.organization(orgId) }
                .onSuccess { snap -> _organization.value = Mapper.toOrganization(snap, snap.dna) }
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
        realtime?.close()
        realtime = null
        currentOrgId = null
        _organization.value = null
        _investigations.value = emptyList()
    }
}
