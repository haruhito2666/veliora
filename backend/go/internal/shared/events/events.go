// Package events implements the immutable event store foundation
// (doc 16 §16.3–§16.6): envelope, taxonomy, appender, projector base.
package events

import (
	"encoding/json"
	"time"

	"github.com/google/uuid"
)

// Categories of the event taxonomy (doc 16 §16.4).
const (
	CatOrganization   = "organization"
	CatWorker         = "worker"
	CatInvestigation  = "investigation"
	CatTask           = "task"
	CatMeeting        = "meeting"
	CatDebate         = "debate"
	CatCommunication  = "communication"
	CatTrust          = "trust"
	CatConstitution   = "constitution"
	CatMemory         = "memory"
	CatEvolution      = "evolution"
	CatEvidence       = "evidence"
	CatVote           = "vote"
)

// Source identifies who emitted an event (doc 16 §16.3).
type Source struct {
	Component    string `json:"component"`
	WorkerID     string `json:"worker_id,omitempty"`
	DepartmentID string `json:"department_id,omitempty"`
}

// CausalChain links an event to its cause (doc 16 §16.3).
type CausalChain struct {
	ParentEventID string `json:"parent_event_id,omitempty"`
	CorrelationID string `json:"correlation_id"`
	RootEventID   string `json:"root_event_id"`
	Depth         int    `json:"depth"`
}

// Metadata carries envelope bookkeeping.
type Metadata struct {
	SchemaVersion  string    `json:"schema_version"`
	RecordedAt     time.Time `json:"recorded_at"`
	SequenceNumber int64     `json:"sequence_number"`
}

// Envelope is the canonical wire/storage shape of every event (doc 16 §16.3).
type Envelope struct {
	EventID        string      `json:"event_id"`
	EventType      string      `json:"event_type"`
	EventCategory  string      `json:"event_category"`
	OrganizationID string      `json:"organization_id"`
	Timestamp      time.Time   `json:"timestamp"`
	Source         Source      `json:"source"`
	Payload        interface{} `json:"payload"`
	CausalChain    CausalChain `json:"causal_chain"`
	Metadata       Metadata    `json:"metadata"`
}

// NewEnvelope builds an envelope with generated IDs and a fresh causal chain.
// parent, when non-nil, links this event into an existing cascade.
func NewEnvelope(orgID, eventType, category, component string, source Source, payload interface{}, parent *Envelope) Envelope {
	now := time.Now().UTC()
	cc := CausalChain{CorrelationID: uuid.NewString(), RootEventID: "", Depth: 0}
	if parent != nil {
		cc = CausalChain{
			ParentEventID: parent.EventID,
			CorrelationID: parent.CausalChain.CorrelationID,
			RootEventID:   parent.CausalChain.RootEventID,
			Depth:         parent.CausalChain.Depth + 1,
		}
	}
	id := uuid.NewString()
	if cc.RootEventID == "" {
		cc.RootEventID = id
	}
	if source.Component == "" {
		source.Component = component
	}
	return Envelope{
		EventID:        id,
		EventType:      eventType,
		EventCategory:  category,
		OrganizationID: orgID,
		Timestamp:      now,
		Source:         source,
		Payload:        payload,
		CausalChain:    cc,
		Metadata: Metadata{
			SchemaVersion: "1.0",
			RecordedAt:    now,
		},
	}
}

// Root returns a new root event (depth 0).
func Root(orgID, eventType, category, component string, source Source, payload interface{}) Envelope {
	e := NewEnvelope(orgID, eventType, category, component, source, payload, nil)
	return e
}

// Child derives a child event from a parent.
func Child(parent Envelope, orgID, eventType, category, component string, source Source, payload interface{}) Envelope {
	return NewEnvelope(orgID, eventType, category, component, source, payload, &parent)
}

// EventType constants (doc 16 §16.4 taxonomy).
const (
	EvOrgCreated   = "organization.created"
	EvOrgEvolved   = "organization.evolved"
	EvOrgDissolved = "organization.dissolved"

	EvWorkerHired        = "worker.hired"
	EvWorkerFired        = "worker.fired"
	EvWorkerPromoted     = "worker.promoted"
	EvWorkerStatus       = "worker.status_changed"
	EvWorkerTerminated   = "worker.terminated"

	EvInvStarted     = "investigation.started"
	EvInvPhase       = "investigation.phase_changed"
	EvInvCompleted   = "investigation.completed"
	EvInvPublished   = "investigation.published"
	EvInvArchived    = "investigation.archived"
	EvInvCancelled   = "investigation.cancelled"
	EvInvFailed      = "investigation.failed"
	EvInvConfidence  = "investigation.confidence"
	EvInvReportReady = "investigation.report_ready"
	EvInvCharter     = "investigation.charter_signed"

	EvTaskAssigned  = "task.assigned"
	EvTaskStarted   = "task.started"
	EvTaskCompleted = "task.completed"
	EvTaskFailed    = "task.failed"

	EvMeetingCalled     = "meeting.called"
	EvMeetingStarted    = "meeting.started"
	EvMeetingMessage    = "meeting.message"
	EvMeetingTurn       = "meeting.turn"
	EvMeetingVote       = "meeting.vote"
	EvMeetingConcluded  = "meeting.concluded"
	EvMeetingMinutes    = "meeting.minutes"

	EvDebateOpened      = "debate.opened"
	EvDebatePosition    = "debate.position"
	EvDebateEvidence    = "debate.evidence"
	EvDebateChallenged  = "debate.challenged"
	EvDebateQuestion    = "debate.question"
	EvDebateAnswer      = "debate.answer"
	EvDebateRebuttal    = "debate.rebuttal"
	EvDebateSynthesis   = "debate.synthesis"
	EvDebateVote        = "debate.vote"
	EvDebateConcluded   = "debate.concluded"

	EvEvidenceSubmitted  = "evidence.submitted"
	EvEvidenceChallenged = "evidence.challenged"
	EvEvidenceUpheld     = "evidence.upheld"
	EvEvidenceRejected   = "evidence.rejected"
	EvEvidenceResolution = "evidence.resolution"

	EvCommSent    = "communication.sent"
	EvCommReceived = "communication.received"
	EvCommReplied = "communication.replied"

	EvTrustUpdated = "trust.updated"

	EvConstitutionAmended = "constitution.amended"

	EvMemoryCreated     = "memory.created"
	EvMemoryReinforced  = "memory.reinforced"
	EvMemoryDecayed     = "memory.decayed"

	EvEvolutionProposed  = "evolution.proposed"
	EvEvolutionApproved  = "evolution.approved"
	EvEvolutionExecuted  = "evolution.executed"
	EvEvolutionRejected  = "evolution.rejected"
	EvEvolutionRolledBack = "evolution.rolled_back"

	EvMessageSent = "message.sent"
)

// CategoryForType maps an event type to its category (used by validation).
var CategoryForType = map[string]string{
	EvOrgCreated: CatOrganization, EvOrgEvolved: CatOrganization, EvOrgDissolved: CatOrganization,
	EvWorkerHired: CatWorker, EvWorkerFired: CatWorker, EvWorkerPromoted: CatWorker,
	EvWorkerStatus: CatWorker, EvWorkerTerminated: CatWorker,
	EvInvStarted: CatInvestigation, EvInvPhase: CatInvestigation, EvInvCompleted: CatInvestigation,
	EvInvPublished: CatInvestigation, EvInvArchived: CatInvestigation, EvInvCancelled: CatInvestigation,
	EvInvFailed: CatInvestigation, EvInvConfidence: CatInvestigation, EvInvReportReady: CatInvestigation,
	EvInvCharter: CatInvestigation,
	EvTaskAssigned: CatTask, EvTaskStarted: CatTask, EvTaskCompleted: CatTask, EvTaskFailed: CatTask,
	EvMeetingCalled: CatMeeting, EvMeetingStarted: CatMeeting, EvMeetingMessage: CatMeeting,
	EvMeetingTurn: CatMeeting, EvMeetingVote: CatMeeting, EvMeetingConcluded: CatMeeting,
	EvMeetingMinutes: CatMeeting,
	EvDebateOpened: CatDebate, EvDebatePosition: CatDebate, EvDebateEvidence: CatDebate,
	EvDebateChallenged: CatDebate, EvDebateQuestion: CatDebate, EvDebateAnswer: CatDebate,
	EvDebateRebuttal: CatDebate, EvDebateSynthesis: CatDebate, EvDebateVote: CatDebate,
	EvDebateConcluded: CatDebate,
	EvEvidenceSubmitted: CatEvidence, EvEvidenceChallenged: CatEvidence, EvEvidenceUpheld: CatEvidence,
	EvEvidenceRejected: CatEvidence, EvEvidenceResolution: CatEvidence,
	EvCommSent: CatCommunication, EvCommReceived: CatCommunication, EvCommReplied: CatCommunication,
	EvTrustUpdated: CatTrust,
	EvConstitutionAmended: CatConstitution,
	EvMemoryCreated: CatMemory, EvMemoryReinforced: CatMemory, EvMemoryDecayed: CatMemory,
	EvEvolutionProposed: CatEvolution, EvEvolutionApproved: CatEvolution, EvEvolutionExecuted: CatEvolution,
	EvEvolutionRejected: CatEvolution, EvEvolutionRolledBack: CatEvolution,
	EvMessageSent: CatCommunication,
}

// PayloadOf decodes a JSON payload into out.
func PayloadOf(ev Envelope, out interface{}) error {
	b, err := json.Marshal(ev.Payload)
	if err != nil {
		return err
	}
	return json.Unmarshal(b, out)
}
