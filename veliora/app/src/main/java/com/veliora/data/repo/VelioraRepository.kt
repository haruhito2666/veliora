package com.veliora.data.repo

import com.veliora.data.api.ApiClient
import com.veliora.data.api.CreateInvestigationRequest
import com.veliora.data.api.ConstitutionDraft
import com.veliora.data.api.CreateOrgRequest
import com.veliora.data.api.CreateOrgResponse
import com.veliora.data.api.InvestigationDto
import com.veliora.data.api.OrgSnapshot
import com.veliora.data.api.OrgStatus
import com.veliora.data.api.ReportDto
import com.veliora.data.api.VelioraApi

/** Typed repository over the authenticated Veliora REST API. */
class VelioraRepository(private val api: VelioraApi = ApiClient.api) {

    suspend fun organizations() = api.organizations()

    suspend fun createOrganization(name: String, template: String, mission: String): CreateOrgResponse =
        api.createOrganization(
            CreateOrgRequest(
                name = name,
                template = template,
                constitution = mission.takeIf { it.isNotBlank() }?.let { ConstitutionDraft(it) },
            ),
        )

    suspend fun organization(orgId: String): OrgSnapshot = api.organization(orgId)

    suspend fun organizationStatus(orgId: String): OrgStatus = api.organizationStatus(orgId)

    suspend fun constitution(orgId: String) = api.constitution(orgId)

    suspend fun evolutionEvents(orgId: String) = api.evolutionEvents(orgId)

    suspend fun workers(orgId: String) = api.workers(orgId).data

    suspend fun communications(orgId: String) = api.communications(orgId)

    suspend fun templates() = api.templates()

    suspend fun investigations(orgId: String) = api.investigations(orgId)

    suspend fun createInvestigation(
        orgId: String,
        prompt: String,
        depth: String = "standard",
    ): InvestigationDto = api.createInvestigation(orgId, CreateInvestigationRequest(prompt, depth))

    suspend fun investigation(orgId: String, invId: String): InvestigationDto =
        api.investigation(orgId, invId)

    suspend fun cancelInvestigation(orgId: String, invId: String) =
        api.cancelInvestigation(orgId, invId)

    suspend fun evidence(orgId: String, invId: String) = api.evidence(orgId, invId)

    suspend fun report(orgId: String, invId: String): ReportDto = api.report(orgId, invId)

    suspend fun meetings(orgId: String) = api.meetings(orgId)

    suspend fun meeting(orgId: String, mtgId: String) = api.meeting(orgId, mtgId)
}

/** Simple UI state wrapper for suspend loads. */
sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Error(val message: String) : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
}
