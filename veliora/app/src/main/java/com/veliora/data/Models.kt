package com.veliora.data

// ═══════════════════════════════════════════════════════════════════════
// DATA MODELS
// ═══════════════════════════════════════════════════════════════════════

// ── Organization ─────────────────────────────────────────────────────

data class Organization(
    val id: String,
    val name: String,
    val template: String,
    val health: Int,
    val dna: OrgDNA,
    val departments: List<Department>,
    val isAlive: Boolean = true,
)

data class OrgDNA(
    val innovation: Int = 0,
    val risk: Int = 0,
    val hierarchy: Int = 0,
    val accuracy: Int = 0,
    val speed: Int = 0,
    val transparency: Int = 0,
    val creativity: Int = 0,
    val longTermThinking: Int = 0,
    val security: Int = 0,
) {
    fun toList(): List<DnaParameter> = listOf(
        DnaParameter("Innovation", innovation, "Will pursue novel approaches"),
        DnaParameter("Risk", risk, "Tolerance for uncertain outcomes"),
        DnaParameter("Hierarchy", hierarchy, "Authority flows top-down"),
        DnaParameter("Accuracy", accuracy, "Demands rigorous evidence"),
        DnaParameter("Speed", speed, "Moves fast, accepts trade-offs"),
        DnaParameter("Transparency", transparency, "Reasoning is visible"),
        DnaParameter("Creativity", creativity, "Explores unconventional paths"),
        DnaParameter("Long-Term", longTermThinking, "Optimizes for years ahead"),
        DnaParameter("Security", security, "Prioritizes safety"),
    )
}

data class DnaParameter(
    val name: String,
    val value: Int,
    val description: String,
)

// ── Department ────────────────────────────────────────────────────────

data class Department(
    val id: String,
    val name: String,
    val shortName: String,
    val trust: Int,
    val workerCount: Int,
    val currentActivity: String,
    /** 0-100 — how far this department has progressed on its current work.
     *  Drives the DepartmentCard color fill. */
    val progress: Int = 0,
    val isAlive: Boolean = true,
    val workers: List<Worker> = emptyList(),
    val tasks: List<OrgTask> = emptyList(),
)

// ── Worker ────────────────────────────────────────────────────────────

data class Worker(
    val id: String,
    val name: String,
    val role: String,
    val department: String,
    val trust: Int,
    val influence: String,
    val currentActivity: String,
    val trustHistory: List<Int> = emptyList(),
    val decisions: List<Decision> = emptyList(),
)

// ── Investigation ────────────────────────────────────────────────────

data class Investigation(
    val id: String,
    val goal: String,
    val phase: InvestigationPhase,
    val phaseProgress: Int = 0,
    val progress: Int,
    val elapsedMinutes: Int,
    val etaMinutes: Int,
    val departmentStatuses: List<DepartmentStatus>,
    val confidence: Int? = null,
)

enum class InvestigationPhase(val label: String, val glyph: String) {
    GATHERING("Gathering", "◐"),
    DEBATE("Debate", "◑"),
    VOTING("Voting", "●"),
    REPORT("Report", "◉"),
    PAUSED("Paused", "⏸"),
}

enum class InvestigationDepth(val label: String) {
    QUICK("Quick"), STANDARD("Standard"), DEEP("Deep")
}

data class DepartmentStatus(
    val departmentName: String,
    val state: DeptState,
    val activity: String,
)

enum class DeptState(val glyph: String) {
    WORKING("◐"),
    IMPLEMENTING("●"),
    WAITING("○"),
    QUEUED("○"),
    COORDINATING("◯"),
}

// ── Evidence ──────────────────────────────────────────────────────────

data class Evidence(
    val id: String,
    val timestamp: String,
    val department: String,
    val type: String,
    val description: String,
    val challengeStatus: ChallengeStatus? = null,
)

enum class ChallengeStatus(val glyph: String, val label: String) {
    CHALLENGED("✗", "challenged"),
    REBUTTED("↺", "rebutted"),
    SURVIVED("✓", "survived"),
}

// ── Meeting ───────────────────────────────────────────────────────────

data class Meeting(
    val id: String,
    val title: String,
    val isLive: Boolean,
    val participants: List<Participant>,
    val elapsedMinutes: Int = 0,
    val phase: String = "Debate",
    val transcript: List<Utterance> = emptyList(),
    val proposal: String? = null,
    val vote: VoteTally? = null,
    val scheduledTime: String? = null,
)

data class Participant(
    val name: String,
    val department: String,
    val isPresent: Boolean,
    val isModerator: Boolean = false,
)

data class Utterance(
    val id: String,
    val speaker: String,
    val department: String,
    val text: String,
    val timestamp: String,
    val isSpeaking: Boolean = false,
    val isTyping: Boolean = false,
)

data class VoteTally(
    val forCount: Int,
    val againstCount: Int,
    val abstainCount: Int,
    val total: Int,
    val threshold: Float,
    val thresholdMet: Boolean,
)

// ── Report ────────────────────────────────────────────────────────────

data class Report(
    val id: String,
    val executiveSummary: String,
    val recommendation: String,
    val confidence: Int,
    val dissentingOpinions: List<DissentingOpinion>,
    val supportingEvidence: List<Evidence>,
    val risks: List<Risk>,
    val opportunities: List<String>,
    val nextSteps: List<NextStep>,
    val fullDiscussionSummary: String,
)

data class DissentingOpinion(
    val department: String,
    val argument: String,
)

data class Risk(val level: RiskLevel?, val description: String)

enum class RiskLevel(val label: String) { HIGH("High"), MEDIUM("Medium"), LOW("Low") }

data class NextStep(
    val description: String,
)

// ── Caspian ───────────────────────────────────────────────────────────

data class CaspianThread(
    val id: String,
    val department: String,
    val recipient: String,
    val channel: CaspianChannel,
    val message: String,
    val reply: String? = null,
    val replyCount: Int? = null,
    val replySummary: String? = null,
    val hasReply: Boolean = false,
    val timestamp: String,
    val isAwaitingReply: Boolean = false,
)

enum class CaspianChannel(val label: String, val icon: String) {
    EMAIL("Email", "✉"),
    TELEGRAM("Telegram", "✈"),
    SLACK("Slack", "#"),
    DISCORD("Discord", "⌘"),
    WHATSAPP("WhatsApp", "☎"),
}

// ── Evolution ────────────────────────────────────────────────────────

data class EvolutionEvent(
    val id: String,
    val type: EvolutionType,
    val description: String,
    val reason: String,
    val daysAgo: Int?,
)

enum class EvolutionType(val glyph: String, val label: String) {
    HIRE("+", "HIRE"),
    FIRE("−", "FIRE"),
    PROMOTE("↑", "PROMOTE"),
    SPLIT("⚡", "SPLIT"),
    MERGE("⋈", "MERGE"),
    POLICY("✎", "POLICY"),
}

// ── Constitution ──────────────────────────────────────────────────────

data class Constitution(
    val mission: String,
    val principles: List<String>,
    val coreValues: List<String>,
    val decisionRules: List<String>,
    val amendments: List<Amendment>,
)

data class Amendment(
    val id: String,
    val text: String,
    val status: AmendmentStatus,
    val daysAgo: Int,
)

enum class AmendmentStatus(val label: String) {
    RATIFIED("Ratified"), DEBATING("Debating")
}

// ── Task ─────────────────────────────────────────────────────────────

data class OrgTask(
    val id: String,
    val description: String,
    val progress: Int, // 0-100, -1 = done
)

// ── Decision ─────────────────────────────────────────────────────────

data class Decision(
    val id: String,
    val text: String,
    val linkType: String, // "meeting" or "report"
)
