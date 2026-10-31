package com.veliora.data.repo

import com.veliora.data.api.CommunicationThreadDto
import com.veliora.data.api.ConstitutionDto
import com.veliora.data.api.ConstitutionDraft
import com.veliora.data.api.CreateInvestigationRequest
import com.veliora.data.api.CreateOrgRequest
import com.veliora.data.api.CreateOrgResponse
import com.veliora.data.api.EvidenceDto
import com.veliora.data.api.EvolutionEventDto
import com.veliora.data.api.InvestigationDto
import com.veliora.data.api.OrgListResponse
import com.veliora.data.api.OrgSnapshot
import com.veliora.data.api.OrgStatus
import com.veliora.data.api.ReportDto
import com.veliora.data.api.TemplatesResponse
import com.veliora.data.sofuu.EmbeddedSofuu
import android.os.SystemClock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URLEncoder

/**
 * Typed repository over the in-process Veliora gateway.
 *
 * The method shapes are the same as the old REST client, but each call now
 * executes inside libsofuu through [EmbeddedSofuu]. There is no Retrofit,
 * no OkHttp request layer, and no Veliora server process.
 */
class VelioraRepository(private val sofuu: EmbeddedSofuu) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private var cachedOrgs: OrgListResponse? = null
    private var cachedOrgsAt = 0L

    suspend fun organizations(force: Boolean = false): OrgListResponse {
        val now = SystemClock.elapsedRealtime()
        cachedOrgs?.takeIf { !force && now - cachedOrgsAt < ORG_LIST_TTL_MS }?.let { return it }
        val result = json.decodeFromString<OrgListResponse>(sofuu.request("GET", "/v1/organizations").body)
        cachedOrgs = result
        cachedOrgsAt = SystemClock.elapsedRealtime()
        return result
    }

    suspend fun createOrganization(
        name: String,
        template: String,
        mission: String,
    ): CreateOrgResponse {
        val request = CreateOrgRequest(
            name = name,
            template = template,
            constitution = mission.takeIf { it.isNotBlank() }?.let { ConstitutionDraft(it) },
        )
        val result = json.decodeFromString<CreateOrgResponse>(
            sofuu.request("POST", "/v1/organizations", json.encodeToString(request)).body,
        )
        cachedOrgs = null
        return result
    }

    suspend fun organization(orgId: String): OrgSnapshot =
        json.decodeFromString(sofuu.request("GET", orgPath(orgId)).body)

    suspend fun organizationStatus(orgId: String): OrgStatus =
        json.decodeFromString(sofuu.request("GET", "${orgPath(orgId)}/status").body)

    suspend fun constitution(orgId: String): ConstitutionDto =
        json.decodeFromString(sofuu.request("GET", "${orgPath(orgId)}/constitution").body)

    suspend fun evolutionEvents(orgId: String): List<EvolutionEventDto> =
        json.decodeFromString(sofuu.request("GET", "${orgPath(orgId)}/evolution/events").body)

    suspend fun communications(orgId: String): List<CommunicationThreadDto> =
        json.decodeFromString(sofuu.request("GET", "${orgPath(orgId)}/communications").body)

    suspend fun templates(): TemplatesResponse =
        json.decodeFromString(sofuu.request("GET", "/v1/organizations/templates").body)

    suspend fun investigations(orgId: String): List<InvestigationDto> =
        json.decodeFromString(sofuu.request("GET", "${orgPath(orgId)}/investigations").body)

    suspend fun createInvestigation(
        orgId: String,
        prompt: String,
        depth: String = "standard",
    ): InvestigationDto {
        val body = json.encodeToString(CreateInvestigationRequest(prompt, depth))
        return json.decodeFromString(
            sofuu.request("POST", "${orgPath(orgId)}/investigations", body).body,
        )
    }

    suspend fun investigation(orgId: String, invId: String): InvestigationDto =
        json.decodeFromString(
            sofuu.request("GET", investigationPath(orgId, invId)).body,
        )

    suspend fun evidence(orgId: String, invId: String): List<EvidenceDto> =
        json.decodeFromString(
            sofuu.request("GET", "${investigationPath(orgId, invId)}/evidence").body,
        )

    suspend fun report(orgId: String, invId: String): ReportDto =
        json.decodeFromString(
            sofuu.request("GET", "${investigationPath(orgId, invId)}/report").body,
        )

    /** Advances the embedded pipeline while a run is in progress. */
    suspend fun pump() = sofuu.tick(100)

    /** Restarts the embedded runtime after a failed initialization. */
    fun retrySofuu() = sofuu.retry()

    suspend fun sdkCapabilities() = sofuu.capabilities()

    suspend fun sdkAbiVersion() = sofuu.abiVersion()

    suspend fun sdkSelfTest() = sofuu.selfTest()

    private fun orgPath(orgId: String) = "/v1/organizations/${segment(orgId)}"

    private fun investigationPath(orgId: String, invId: String) =
        "${orgPath(orgId)}/investigations/${segment(invId)}"

    private fun segment(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private companion object {
        const val ORG_LIST_TTL_MS = 5_000L
    }
}
