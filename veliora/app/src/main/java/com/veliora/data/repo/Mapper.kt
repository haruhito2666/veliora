package com.veliora.data.repo

import com.veliora.data.CaspianThread
import com.veliora.data.CaspianChannel
import com.veliora.data.ChallengeStatus
import com.veliora.data.Constitution
import com.veliora.data.Department
import com.veliora.data.Evidence
import com.veliora.data.EvolutionEvent
import com.veliora.data.EvolutionType
import com.veliora.data.Investigation
import com.veliora.data.InvestigationPhase
import com.veliora.data.Organization
import com.veliora.data.OrgDNA
import com.veliora.data.Report
import com.veliora.data.Worker
import com.veliora.data.api.ChallengeDto
import com.veliora.data.api.CommunicationThreadDto
import com.veliora.data.api.ConstitutionDto
import com.veliora.data.api.DepartmentDto
import com.veliora.data.api.EvidenceDto
import com.veliora.data.api.EvolutionEventDto
import com.veliora.data.api.InvestigationDto
import com.veliora.data.api.OrgSnapshot
import com.veliora.data.api.ReportDto
import com.veliora.data.api.WorkerDto

/**
 * Maps server DTOs to the existing UI models (Models.kt) so screens keep
 * their current structure while sourcing real data.
 */
object Mapper {

    fun toOrganization(snap: OrgSnapshot, dna: Map<String, Double>): Organization {
        return Organization(
            id = snap.organizationId,
            name = snap.name,
            template = snap.template,
            health = snap.health.toInt(),
            dna = OrgDNA(
                innovation = ((dna["innovation"] ?: 0.0) * 100).toInt(),
                risk = ((dna["risk"] ?: 0.0) * 100).toInt(),
                hierarchy = ((dna["hierarchy"] ?: 0.0) * 100).toInt(),
                accuracy = ((dna["accuracy"] ?: 0.0) * 100).toInt(),
                speed = ((dna["speed"] ?: 0.0) * 100).toInt(),
                transparency = ((dna["transparency"] ?: 0.0) * 100).toInt(),
                creativity = ((dna["creativity"] ?: 0.0) * 100).toInt(),
                longTermThinking = ((dna["long_term_thinking"] ?: 0.0) * 100).toInt(),
                security = ((dna["security"] ?: 0.0) * 100).toInt(),
            ),
            departments = snap.departments.map { toDepartment(it) },
            isAlive = snap.state == "active",
        )
    }

    fun toDepartment(d: DepartmentDto): Department {
        return Department(
            id = d.departmentId,
            name = d.name,
            shortName = d.name.take(6),
            trust = (d.influence * 100).toInt(),
            workerCount = d.workers.size,
            currentActivity = d.role,
        )
    }

    fun toWorker(w: WorkerDto): Worker {
        return Worker(
            id = w.workerId,
            name = w.role,
            role = w.role,
            department = w.departmentId,
            trust = (w.trustScore * 100).toInt(),
            influence = if (w.trustScore >= 0.9) "high" else if (w.trustScore >= 0.6) "medium" else "low",
            currentActivity = w.state,
        )
    }

    fun toInvestigation(dto: InvestigationDto): Investigation {
        val phase = when (dto.state) {
            "researching" -> InvestigationPhase.GATHERING
            "debating" -> InvestigationPhase.DEBATE
            "vote" -> InvestigationPhase.VOTING
            "completed", "archived" -> InvestigationPhase.REPORT
            else -> InvestigationPhase.GATHERING
        }
        return Investigation(
            id = dto.investigationId,
            goal = dto.prompt,
            phase = phase,
            progress = phaseProgress(dto.state),
            phaseProgress = phaseProgress(dto.state),
            elapsedMinutes = 0,
            etaMinutes = 0,
            departmentStatuses = emptyList(),
            confidence = if (dto.confidence > 0) (dto.confidence * 100).toInt() else null,
        )
    }

    fun toEvidence(e: EvidenceDto): Evidence {
        val status = when (e.status) {
            "challenged" -> ChallengeStatus.CHALLENGED
            "accepted" -> ChallengeStatus.SURVIVED
            "rejected" -> ChallengeStatus.REBUTTED
            else -> null
        }
        return Evidence(
            id = e.evidenceId,
            timestamp = e.submittedAt,
            department = e.submittedBy,
            type = e.sourceType,
            description = e.summary,
            challengeStatus = status,
        )
    }

    fun toReport(r: ReportDto): Report {
        return Report(
            id = r.reportId,
            executiveSummary = r.executiveSummary,
            recommendation = r.recommendation,
            confidence = (r.confidence * 100).toInt(),
            dissentingOpinions = r.dissentingOpinions.map {
                com.veliora.data.DissentingOpinion(department = "", argument = it)
            },
            supportingEvidence = r.supportingEvidence.map {
                Evidence(id = "", timestamp = "", department = "", type = "evidence", description = it)
            },
            risks = r.risks.map { com.veliora.data.Risk(level = null, description = it) },
            opportunities = r.opportunities,
            nextSteps = r.nextSteps.map { com.veliora.data.NextStep(description = it) },
            fullDiscussionSummary = r.fullDiscussion,
        )
    }

    fun toCaspianThread(c: CommunicationThreadDto): CaspianThread {
        return CaspianThread(
            id = c.logId,
            department = if (c.direction == "inbound") "External" else "Organization",
            recipient = c.contactName,
            channel = when (c.channel) {
                "telegram" -> CaspianChannel.TELEGRAM
                "slack" -> CaspianChannel.SLACK
                "discord" -> CaspianChannel.DISCORD
                "whatsapp" -> CaspianChannel.WHATSAPP
                else -> CaspianChannel.EMAIL
            },
            message = c.content,
            reply = if (c.direction == "inbound") c.content else null,
            hasReply = c.status == "replied",
            isAwaitingReply = c.status == "unsent" || c.status == "sent",
            timestamp = c.sentAt ?: "",
        )
    }

    fun toEvolutionEvent(e: EvolutionEventDto): EvolutionEvent {
        val type = when (e.eventType) {
            "hire" -> EvolutionType.HIRE
            "fire" -> EvolutionType.FIRE
            "promote" -> EvolutionType.PROMOTE
            "split_department" -> EvolutionType.SPLIT
            "merge_department" -> EvolutionType.MERGE
            else -> EvolutionType.POLICY
        }
        return EvolutionEvent(
            id = e.eventId,
            type = type,
            description = e.description,
            reason = e.rationale,
            daysAgo = e.proposedAt.takeIf { it.isNotBlank() }?.let { raw ->
                runCatching {
                    val then = java.time.OffsetDateTime.parse(raw)
                    java.time.Duration.between(then, java.time.OffsetDateTime.now()).toDays().coerceAtLeast(0).toInt()
                }.getOrNull()
            },
        )
    }

    fun toConstitution(c: ConstitutionDto): Constitution {
        val decisionRules = c.decisionRules.keys.map { "$it: ${c.decisionRules[it]}" }
        return Constitution(
            mission = c.mission,
            principles = c.principles,
            coreValues = c.values,
            decisionRules = decisionRules,
            amendments = emptyList(),
        )
    }

    fun toChallenge(dto: ChallengeDto, evidenceId: String): Evidence {
        return Evidence(
            id = dto.challengeId,
            timestamp = "",
            department = dto.by,
            type = "challenge",
            description = dto.critique,
            challengeStatus = ChallengeStatus.CHALLENGED,
        )
    }

    private fun phaseProgress(state: String): Int = when (state) {
        "planning" -> 10
        "researching" -> 35
        "analyzing" -> 50
        "debating" -> 65
        "synthesizing", "vote" -> 80
        "reporting" -> 92
        "completed", "archived" -> 100
        else -> 5
    }

}
