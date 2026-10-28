package com.veliora.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Organizations ───────────────────────────────────────────────────────

/** Generic list wrapper for endpoints returning `{ "data": [...] }`. */
@Serializable
data class ListResponse<T>(
    val data: List<T> = emptyList(),
)

@Serializable
data class CreateOrgRequest(
    val name: String,
    val template: String,
    val constitution: ConstitutionDraft? = null,
)

@Serializable
data class ConstitutionDraft(
    val mission: String,
)

@Serializable
data class CreateOrgResponse(
    @SerialName("organization_id") val organizationId: String,
    val name: String,
    val state: String,
    val template: String,
    @SerialName("constitution_id") val constitutionId: String? = null,
    @SerialName("dna_id") val dnaId: String? = null,
    val departments: List<DepartmentDto> = emptyList(),
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class OrgLite(
    @SerialName("organization_id") val organizationId: String,
    val name: String,
    val template: String,
    val state: String,
    val health: Double = 0.0,
)

@Serializable
data class OrgListResponse(val data: List<OrgLite> = emptyList())

@Serializable
data class OrgSnapshot(
    @SerialName("organization_id") val organizationId: String,
    val name: String,
    val state: String,
    val template: String,
    val health: Double = 0.0,
    val dna: Map<String, Double> = emptyMap(),
    val departments: List<DepartmentDto> = emptyList(),
    val workers: List<WorkerDto> = emptyList(),
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class DepartmentDto(
    @SerialName("department_id") val departmentId: String,
    @SerialName("organization_id") val organizationId: String = "",
    val name: String,
    val role: String = "",
    val charter: String = "",
    val workers: List<String> = emptyList(),
    val influence: Double = 0.0,
    val load: Double = 0.0,
)

@Serializable
data class WorkerDto(
    @SerialName("worker_id") val workerId: String,
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("department_id") val departmentId: String = "",
    val name: String = "",
    val role: String,
    val state: String = "active",
    @SerialName("trust_score") val trustScore: Double = 0.0,
    @SerialName("current_task_id") val currentTaskId: String = "",
    val promotions: Int = 0,
    @SerialName("joined_at") val joinedAt: String = "",
)

@Serializable
data class OrgStatus(
    @SerialName("organization_id") val organizationId: String,
    val state: String,
    val health: Double = 0.0,
    val departments: List<DeptStatusDto> = emptyList(),
    val counts: OrgCounts = OrgCounts(),
)

@Serializable
data class DeptStatusDto(
    val name: String,
    @SerialName("worker_state") val workerState: String = "idle",
    val activity: String = "",
    @SerialName("current_task") val currentTask: String = "",
    val artifacts: Int = 0,
    @SerialName("open_challenges") val openChallenges: Int = 0,
    @SerialName("caspian_open") val caspianOpen: Int = 0,
    val trust: Double = 0.0,
)

@Serializable
data class OrgCounts(
    @SerialName("investigations_active") val investigationsActive: Int = 0,
    val meetings: Int = 0,
    val messages: Int = 0,
)

@Serializable
data class TemplateSummary(
    val id: String,
    val name: String,
    val description: String = "",
    val departments: List<String> = emptyList(),
)

@Serializable
data class TemplatesResponse(val templates: List<TemplateSummary> = emptyList())

// ── Investigations ──────────────────────────────────────────────────────

@Serializable
data class CreateInvestigationRequest(
    val prompt: String,
    val depth: String = "standard",
)

@Serializable
data class InvestigationDto(
    @SerialName("investigation_id") val investigationId: String,
    @SerialName("organization_id") val organizationId: String = "",
    val title: String,
    val prompt: String,
    val state: String,
    @SerialName("current_phase") val currentPhase: String = "",
    val charter: CharterDto? = null,
    val plan: PlanDto = PlanDto(),
    @SerialName("cycle_count") val cycleCount: Int = 1,
    val confidence: Double = 0.0,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("completed_at") val completedAt: String? = null,
)

@Serializable
data class CharterDto(
    val goal: String,
    val departments: List<String> = emptyList(),
    @SerialName("success_criteria") val successCriteria: List<String> = emptyList(),
    @SerialName("confidence_threshold") val confidenceThreshold: Double = 0.72,
    val priority: String = "normal",
)

@Serializable
data class PlanDto(val tasks: List<TaskDto> = emptyList())

@Serializable
data class TaskDto(
    @SerialName("task_id") val taskId: String = "",
    val department: String = "",
    val description: String = "",
    val deliverables: String = "",
    val status: String = "pending",
)

// ── Evidence ────────────────────────────────────────────────────────────

@Serializable
data class EvidenceDto(
    @SerialName("evidence_id") val evidenceId: String,
    @SerialName("investigation_id") val investigationId: String = "",
    @SerialName("submitted_by") val submittedBy: String = "",
    @SerialName("source_type") val sourceType: String = "",
    val summary: String,
    val confidence: Double = 0.0,
    val status: String = "submitted",
    @SerialName("challenge_count") val challengeCount: Int = 0,
    @SerialName("submitted_at") val submittedAt: String = "",
    val challenges: List<ChallengeDto> = emptyList(),
)

@Serializable
data class ChallengeDto(
    @SerialName("challenge_id") val challengeId: String,
    val by: String = "",
    val critique: String = "",
    val resolution: String = "",
)

// ── Meetings ────────────────────────────────────────────────────────────

@Serializable
data class MeetingDto(
    @SerialName("meeting_id") val meetingId: String,
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("investigation_id") val investigationId: String = "",
    val title: String,
    val state: String,
    val agenda: List<AgendaItemDto> = emptyList(),
    val participants: List<String> = emptyList(),
    val chair: String = "",
    @SerialName("called_at") val calledAt: String = "",
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
)

@Serializable
data class AgendaItemDto(
    val id: String = "",
    val title: String,
    val type: String = "status",
    @SerialName("time_budget_min") val timeBudgetMin: Int = 5,
)

// ── Evolution ───────────────────────────────────────────────────────────

@Serializable
data class EvolutionEventDto(
    @SerialName("event_id") val eventId: String,
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("event_type") val eventType: String,
    @SerialName("target_type") val targetType: String = "",
    @SerialName("target_id") val targetId: String = "",
    val description: String = "",
    val rationale: String = "",
    val status: String = "proposed",
    @SerialName("triggered_by_rule") val triggeredByRule: String = "",
    @SerialName("proposed_at") val proposedAt: String = "",
)

// ── Constitution ────────────────────────────────────────────────────────

@Serializable
data class ConstitutionDto(
    val mission: String = "",
    val principles: List<String> = emptyList(),
    val values: List<String> = emptyList(),
    val ethics: List<String> = emptyList(),
    @SerialName("decision_rules") val decisionRules: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
)

// ── Communications (Caspian) ────────────────────────────────────────────

@Serializable
data class CommunicationThreadDto(
    @SerialName("log_id") val logId: String,
    val channel: String,
    @SerialName("contact_name") val contactName: String = "",
    val direction: String = "outbound",
    val subject: String = "",
    val content: String = "",
    val status: String = "unsent",
    @SerialName("sent_at") val sentAt: String? = null,
    @SerialName("received_at") val receivedAt: String? = null,
    val error: String = "",
    @SerialName("outreach_status") val outreachStatus: String = "",
)

// ── Report ──────────────────────────────────────────────────────────────

@Serializable
data class ReportDto(
    @SerialName("report_id") val reportId: String,
    @SerialName("investigation_id") val investigationId: String,
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("executive_summary") val executiveSummary: String = "",
    val recommendation: String = "",
    val confidence: Double = 0.0,
    @SerialName("dissenting_opinions") val dissentingOpinions: List<String> = emptyList(),
    @SerialName("supporting_evidence") val supportingEvidence: List<String> = emptyList(),
    val risks: List<String> = emptyList(),
    val opportunities: List<String> = emptyList(),
    @SerialName("next_steps") val nextSteps: List<String> = emptyList(),
    @SerialName("full_discussion") val fullDiscussion: String = "",
)

// ── Error envelope ──────────────────────────────────────────────────────

@Serializable
data class ApiError(
    val error: ApiErrorBody = ApiErrorBody(),
)

@Serializable
data class ApiErrorBody(
    val code: String = "",
    val message: String = "",
)
