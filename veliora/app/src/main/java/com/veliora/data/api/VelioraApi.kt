package com.veliora.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Authless REST interface for a trusted Veliora instance. */
interface VelioraApi {

    @GET("v1/organizations/templates")
    suspend fun templates(): TemplatesResponse

    @GET("v1/organizations")
    suspend fun organizations(): OrgListResponse

    @POST("v1/organizations")
    suspend fun createOrganization(@Body body: CreateOrgRequest): CreateOrgResponse

    @GET("v1/organizations/{orgId}")
    suspend fun organization(@Path("orgId") orgId: String): OrgSnapshot

    @GET("v1/organizations/{orgId}/status")
    suspend fun organizationStatus(@Path("orgId") orgId: String): OrgStatus

    @GET("v1/organizations/{orgId}/constitution")
    suspend fun constitution(@Path("orgId") orgId: String): ConstitutionDto

    @GET("v1/organizations/{orgId}/evolution/events")
    suspend fun evolutionEvents(@Path("orgId") orgId: String): List<EvolutionEventDto>

    @GET("v1/organizations/{orgId}/workers")
    suspend fun workers(@Path("orgId") orgId: String): ListResponse<WorkerDto>

    @GET("v1/organizations/{orgId}/communications")
    suspend fun communications(@Path("orgId") orgId: String): List<CommunicationThreadDto>

    @GET("v1/organizations/{orgId}/investigations")
    suspend fun investigations(@Path("orgId") orgId: String): List<InvestigationDto>

    @POST("v1/organizations/{orgId}/investigations")
    suspend fun createInvestigation(
        @Path("orgId") orgId: String,
        @Body body: CreateInvestigationRequest,
    ): InvestigationDto

    @GET("v1/organizations/{orgId}/investigations/{invId}")
    suspend fun investigation(
        @Path("orgId") orgId: String,
        @Path("invId") invId: String,
    ): InvestigationDto

    @POST("v1/organizations/{orgId}/investigations/{invId}/cancel")
    suspend fun cancelInvestigation(
        @Path("orgId") orgId: String,
        @Path("invId") invId: String,
    ): Map<String, Boolean>

    @GET("v1/organizations/{orgId}/investigations/{invId}/evidence")
    suspend fun evidence(
        @Path("orgId") orgId: String,
        @Path("invId") invId: String,
    ): List<EvidenceDto>

    @GET("v1/organizations/{orgId}/investigations/{invId}/report")
    suspend fun report(
        @Path("orgId") orgId: String,
        @Path("invId") invId: String,
    ): ReportDto

    @GET("v1/organizations/{orgId}/meetings")
    suspend fun meetings(@Path("orgId") orgId: String): List<MeetingDto>

    @GET("v1/organizations/{orgId}/meetings/{mtgId}")
    suspend fun meeting(
        @Path("orgId") orgId: String,
        @Path("mtgId") mtgId: String,
    ): MeetingDto
}

/** Generic list wrapper for endpoints returning `{ "data": [...] }`. */
@kotlinx.serialization.Serializable
data class ListResponse<T>(
    val data: List<T> = emptyList(),
)
