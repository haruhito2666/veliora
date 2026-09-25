package dna

import (
	"testing"
)

func TestDeriveDebateParams(t *testing.T) {
	// Startup DNA: accuracy 0.8, speed 0.6, hierarchy 0.5, transparency 0.75, risk 0.6, long-term 0.6.
	d := &DNA{
		Innovation: 0.7, Risk: 0.6, Hierarchy: 0.5, Accuracy: 0.8,
		Speed: 0.6, Transparency: 0.75, Creativity: 0.65,
		LongTermThinking: 0.6, Security: 0.55,
	}
	p := DeriveDebateParams(d)
	if p.OpeningTurnTokens <= 0 {
		t.Fatalf("opening tokens = %d", p.OpeningTurnTokens)
	}
	// floor(3 * 1.8 * 0.5) = floor(2.7) = 2
	if p.CrossExamineRounds != 2 {
		t.Fatalf("cross-exam rounds = %d, want 2", p.CrossExamineRounds)
	}
	// 1 + floor(0.6) = 1
	if p.RebuttalRounds != 1 {
		t.Fatalf("rebuttal rounds = %d, want 1", p.RebuttalRounds)
	}
	// transparency 0.75 > 0.3 → live view; < 0.7? 0.75 > 0.7 → publish transcript
	if !p.LiveView {
		t.Fatal("live view should be on")
	}
	if !p.PublishTranscript {
		t.Fatal("transcript should publish at transparency 0.75")
	}
	// hierarchy 0.5 → not open challenge
	if p.OpenChallenge {
		t.Fatal("open challenge should be off at hierarchy 0.5")
	}
}

func TestOpenChallengeAtLowHierarchy(t *testing.T) {
	d := &DNA{Hierarchy: 0.2, Accuracy: 0.7, Speed: 0.5, Transparency: 0.8,
		Risk: 0.5, LongTermThinking: 0.5}
	p := DeriveDebateParams(d)
	if !p.OpenChallenge {
		t.Fatal("hierarchy 0.2 should permit open challenges")
	}
}

func TestPersonalityDeltaBounds(t *testing.T) {
	d := &DNA{Accuracy: 0.5}
	for seed := 0.0; seed < 20; seed++ {
		v := PersonalityDelta(d, seed)
		for k, val := range v {
			if val < 0 || val > 1 {
				t.Fatalf("dim %s out of bounds: %v", k, val)
			}
		}
	}
}

func TestMeetingCadence(t *testing.T) {
	d := &DNA{Speed: 0.6, LongTermThinking: 0.6}
	days := MeetingCadence(d, "all_hands") // 7 * 1.6/1.6 = 7
	if days < 6.9 || days > 7.1 {
		t.Fatalf("all-hands cadence = %v, want ~7", days)
	}
}
