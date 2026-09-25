package events

import (
	"context"
	"testing"
)

func TestRootAndChildCausalChain(t *testing.T) {
	root := Root("org1", EvOrgCreated, CatOrganization, "org-manager",
		Source{Component: "org-manager"}, map[string]interface{}{"name": "Startup"})

	if root.CausalChain.Depth != 0 {
		t.Fatalf("root depth = %d, want 0", root.CausalChain.Depth)
	}
	if root.CausalChain.RootEventID != root.EventID {
		t.Fatalf("root event id mismatch: %s != %s", root.CausalChain.RootEventID, root.EventID)
	}
	if root.CausalChain.CorrelationID == "" {
		t.Fatal("root correlation id empty")
	}

	child := Child(root, "org1", EvWorkerHired, CatWorker, "org-manager",
		Source{Component: "org-manager"}, map[string]interface{}{"worker_id": "w1"})

	if child.CausalChain.Depth != 1 {
		t.Fatalf("child depth = %d, want 1", child.CausalChain.Depth)
	}
	if child.CausalChain.ParentEventID != root.EventID {
		t.Fatalf("child parent = %s, want %s", child.CausalChain.ParentEventID, root.EventID)
	}
	if child.CausalChain.RootEventID != root.EventID {
		t.Fatalf("child root mismatch")
	}
	if child.CausalChain.CorrelationID != root.CausalChain.CorrelationID {
		t.Fatalf("child correlation mismatch")
	}
}

func TestCategoryForTypeComplete(t *testing.T) {
	// Every constant we define must have a category mapping.
	for _, et := range []string{
		EvOrgCreated, EvOrgEvolved, EvOrgDissolved,
		EvWorkerHired, EvWorkerFired, EvWorkerPromoted, EvWorkerStatus, EvWorkerTerminated,
		EvInvStarted, EvInvPhase, EvInvCompleted, EvInvPublished, EvInvArchived,
		EvInvCancelled, EvInvFailed, EvInvConfidence, EvInvReportReady, EvInvCharter,
		EvTaskAssigned, EvTaskStarted, EvTaskCompleted, EvTaskFailed,
		EvMeetingCalled, EvMeetingStarted, EvMeetingMessage, EvMeetingTurn,
		EvMeetingVote, EvMeetingConcluded, EvMeetingMinutes,
		EvDebateOpened, EvDebatePosition, EvDebateEvidence, EvDebateChallenged,
		EvDebateQuestion, EvDebateAnswer, EvDebateRebuttal, EvDebateSynthesis,
		EvDebateVote, EvDebateConcluded,
		EvEvidenceSubmitted, EvEvidenceChallenged, EvEvidenceUpheld,
		EvEvidenceRejected, EvEvidenceResolution,
		EvCommSent, EvCommReceived, EvCommReplied,
		EvTrustUpdated, EvConstitutionAmended,
		EvMemoryCreated, EvMemoryReinforced, EvMemoryDecayed,
		EvEvolutionProposed, EvEvolutionApproved, EvEvolutionExecuted,
		EvEvolutionRejected, EvEvolutionRolledBack,
		EvMessageSent,
	} {
		if _, ok := CategoryForType[et]; !ok {
			t.Fatalf("event type %s has no category mapping", et)
		}
	}
}

var _ = context.Background
