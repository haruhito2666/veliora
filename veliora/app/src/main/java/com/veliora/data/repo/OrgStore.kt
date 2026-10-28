package com.veliora.data.repo

import com.veliora.data.DepartmentStatus
import com.veliora.data.Investigation
import com.veliora.data.Organization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Central store for organization data.
 *
 * The old implementation subscribed to server-sent events. The embedded
 * runtime has no background HTTP pump, so this store polls the in-process
 * gateway and advances its event loop between reads.
 *
 * Polling is attached to the lifecycle of the screens that need it
 * ([attach]/[detach]). That keeps the native timer loop and Clerk token
 * refresh from running while the app is backgrounded or no live screen is
 * visible.
 */
class OrgStore(
    private val repository: VelioraRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private val _organization = MutableStateFlow<Organization?>(null)
    val organization: StateFlow<Organization?> = _organization.asStateFlow()

    private val _investigations = MutableStateFlow<List<Investigation>>(emptyList())
    val investigations: StateFlow<List<Investigation>> = _investigations.asStateFlow()

    private val _departmentStatuses = MutableStateFlow<List<DepartmentStatus>>(emptyList())
    val departmentStatuses: StateFlow<List<DepartmentStatus>> = _departmentStatuses.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    @Volatile private var currentOrgId: String? = null

    /** The organization currently loaded by the live screens, if any. */
    val activeOrgId: String?
        get() = currentOrgId

    private var pollJob: Job? = null
    private var loadJob: Job? = null
    private var attachedCount = 0

    /**
     * Marks a screen as a live consumer of [orgId]. The first attachment
     * starts loading and polling; further attachments share the same poll.
     */
    fun attach(orgId: String) {
        var load = false
        var poll = false
        synchronized(lock) {
            if (currentOrgId != orgId) {
                currentOrgId = orgId
                attachedCount = 0
                loadJob?.cancel()
                pollJob?.cancel()
                loadJob = null
                pollJob = null
                _organization.value = null
                _investigations.value = emptyList()
                _departmentStatuses.value = emptyList()
                _error.value = null
                _actionError.value = null
                _loading.value = false
                load = true
                poll = true
            } else {
                load = _organization.value == null && !_loading.value
                poll = pollJob?.isActive != true
            }
            attachedCount += 1
        }
        if (load) startLoad(orgId)
        if (poll) startPoll(orgId)
    }

    /** Releases a screen's attachment; polling stops with the last one. */
    fun detach(orgId: String) {
        synchronized(lock) {
            if (currentOrgId != orgId) return
            attachedCount = (attachedCount - 1).coerceAtLeast(0)
            if (attachedCount == 0) {
                pollJob?.cancel()
                pollJob = null
            }
        }
    }

    /** Retries a failed runtime or organization load. */
    fun retry() {
        val orgId = synchronized(lock) { currentOrgId } ?: return
        repository.retrySofuu()
        synchronized(lock) {
            _error.value = null
            _actionError.value = null
            loadJob?.cancel()
            loadJob = null
            pollJob?.cancel()
            pollJob = null
        }
        startLoad(orgId)
        startPoll(orgId)
    }

    private fun startLoad(orgId: String) {
        synchronized(lock) {
            loadJob?.cancel()
            _loading.value = true
            _error.value = null
            loadJob = scope.launch {
                try {
                    val snap = repository.organization(orgId)
                    val investigations = repository.investigations(orgId)
                    if (currentOrgId == orgId) {
                        _organization.value = Mapper.toOrganization(snap, snap.dna)
                        _investigations.value = investigations.map { Mapper.toInvestigation(it) }
                        _error.value = null
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (currentOrgId == orgId) {
                        _error.value = e.message ?: "Failed to load organization"
                    }
                } finally {
                    if (currentOrgId == orgId) _loading.value = false
                }
            }
        }
    }

    private fun startPoll(orgId: String) {
        synchronized(lock) {
            pollJob?.cancel()
            pollJob = scope.launch { pollLoop(orgId) }
        }
    }

    private suspend fun pollLoop(orgId: String) {
        var statusTick = 0
        while (currentCoroutineContext().isActive && currentOrgId == orgId) {
            val active = _investigations.value.any { !it.isTerminal }
            delay(if (active) 250 else 2_000)
            if (currentOrgId != orgId) break

            // Drain one bounded slice of the runtime loop so scripted or
            // local-model investigation steps can progress while the UI is
            // idle. Idle organizations only need an occasional refresh.
            if (active) runCatching { repository.pump() }

            runCatching { repository.organization(orgId) }
                .onSuccess { snap ->
                    if (currentOrgId == orgId) {
                        _organization.value = Mapper.toOrganization(snap, snap.dna)
                        _error.value = null
                    }
                }

            runCatching { repository.investigations(orgId) }
                .onSuccess { list ->
                    if (currentOrgId == orgId) {
                        _investigations.value = list.map { Mapper.toInvestigation(it) }
                        _error.value = null
                    }
                }

            // Active investigations get a status refresh about once a
            // second; idle organizations every four seconds.
            val refreshStatus = if (active) statusTick % 4 == 0 else statusTick % 2 == 0
            statusTick += 1
            if (refreshStatus) {
                runCatching { repository.organizationStatus(orgId) }
                    .onSuccess { status ->
                        if (currentOrgId == orgId) {
                            applyDepartmentStatuses(Mapper.toDepartmentStatuses(status))
                        }
                    }
            }
        }
    }

    private fun applyDepartmentStatuses(statuses: List<DepartmentStatus>) {
        _departmentStatuses.value = statuses
        if (statuses.isEmpty()) return
        val byName = statuses.associateBy { it.departmentName }
        _organization.update { org ->
            org?.copy(
                departments = org.departments.map { department ->
                    val status = byName[department.name] ?: return@map department
                    department.copy(currentActivity = status.activity)
                },
            )
        }
    }

    /** Creates an investigation and returns its id. */
    suspend fun createInvestigation(goal: String, depth: String = "standard"): String? {
        val orgId = synchronized(lock) { currentOrgId }
        if (orgId == null) {
            _actionError.value = "No organization is loaded. Go back and open an organization."
            return null
        }
        return try {
            val dto = repository.createInvestigation(orgId, goal, depth)
            _actionError.value = null
            if (currentOrgId == orgId) {
                _investigations.value =
                    (listOf(Mapper.toInvestigation(dto)) + _investigations.value)
                        .distinctBy { it.id }
                // Re-evaluate the active-poll cadence immediately instead of
                // waiting for the previous idle delay to expire.
                synchronized(lock) {
                    if (currentOrgId == orgId && attachedCount > 0) {
                        pollJob?.cancel()
                        pollJob = null
                        startPoll(orgId)
                    }
                }
            }
            dto.investigationId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _actionError.value = e.message ?: "Failed to create investigation"
            null
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun clear() {
        synchronized(lock) {
            attachedCount = 0
            currentOrgId = null
            loadJob?.cancel()
            loadJob = null
            pollJob?.cancel()
            pollJob = null
        }
        _organization.value = null
        _investigations.value = emptyList()
        _departmentStatuses.value = emptyList()
        _loading.value = false
        _error.value = null
        _actionError.value = null
    }
}
