// Package dna implements the DNA Engine (doc 21, doc 10 §7): the nine
// organizational parameters and their projection into debate/meeting
// protocol parameters.
package dna

import (
	"context"
	"encoding/json"
	"errors"
	"math"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// ErrNotFound is returned when no DNA profile exists.
var ErrNotFound = errors.New("not found")

// Service is the DNA Engine.
type Service struct {
	pool *pgxpool.Pool
}

// NewService builds the DNA Engine.
func NewService(pool *pgxpool.Pool) *Service {
	return &Service{pool: pool}
}

// DNA is the nine-parameter vector (doc 13 §13.4.1), values 0..1.
type DNA struct {
	Innovation      float64 `json:"innovation"`
	Risk            float64 `json:"risk"`
	Hierarchy       float64 `json:"hierarchy"`
	Accuracy        float64 `json:"accuracy"`
	Speed           float64 `json:"speed"`
	Transparency    float64 `json:"transparency"`
	Creativity      float64 `json:"creativity"`
	LongTermThinking float64 `json:"long_term_thinking"`
	Security        float64 `json:"security"`
}

// GetCurrent returns the org's current DNA profile.
func (s *Service) GetCurrent(ctx context.Context, orgID string) (*DNA, error) {
	var params []byte
	err := s.pool.QueryRow(ctx, `
		SELECT parameters FROM dna_profiles
		WHERE organization_id = $1 AND is_current = true
		ORDER BY version DESC LIMIT 1`, orgID).Scan(&params)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, ErrNotFound
		}
		return nil, err
	}
	var d DNA
	if err := json.Unmarshal(params, &d); err != nil {
		return nil, err
	}
	return &d, nil
}

// DebateParams are the DNA-derived debate protocol parameters (doc 10 §7.1).
type DebateParams struct {
	OpeningTurnTokens  int `json:"opening_turn_tokens"`
	EvidenceTurnTokens int `json:"evidence_turn_tokens"`
	CrossExamineRounds int `json:"cross_examine_rounds"`
	RebuttalRounds     int `json:"rebuttal_rounds"`
	VoteDeadlineMin    int `json:"vote_deadline_min"`
	LiveView           bool `json:"live_view"`
	PublishTranscript  bool `json:"publish_transcript"`
	OpenChallenge      bool `json:"open_challenge"` // hierarchy < 0.3 → any agent may challenge
}

// DeriveDebateParams projects DNA onto debate protocol parameters (doc 10 §7.1).
func DeriveDebateParams(d *DNA) DebateParams {
	p := DebateParams{}
	p.OpeningTurnTokens = int(300.0 * (1 + d.Accuracy) / (1 + d.Speed))
	p.EvidenceTurnTokens = int(400.0 * (1 + d.Accuracy) / (1 + d.Speed))
	p.CrossExamineRounds = int(math.Floor(3 * (1 + d.Accuracy) * (1 - d.Hierarchy)))
	if p.CrossExamineRounds < 1 {
		p.CrossExamineRounds = 1
	}
	p.RebuttalRounds = 1 + int(math.Floor(d.Risk))
	p.VoteDeadlineMin = int(30.0 * (1 + d.LongTermThinking) / (1 + d.Speed))
	p.LiveView = d.Transparency > 0.3
	p.PublishTranscript = d.Transparency > 0.7
	p.OpenChallenge = d.Hierarchy < 0.3
	return p
}

// MeetingCadence returns the base meeting interval days for a type
// (doc 20 §6.2), DNA-modulated.
func MeetingCadence(d *DNA, meetingType string) float64 {
	base := map[string]float64{
		"all_hands": 7, "departmental": 2, "cross_functional": 3,
		"board": 0, "emergency": 0,
	}[meetingType]
	if base == 0 {
		return 0
	}
	return base * (1 + d.LongTermThinking) / (1 + d.Speed)
}

// PersonalityDelta derives a worker's personality vector from org DNA plus
// an individual delta (doc 05 §2.3).
func PersonalityDelta(d *DNA, seed float64) map[string]float64 {
	// Deterministic small delta in [-0.1, +0.1] per dimension from the seed.
	out := map[string]float64{}
	base := map[string]float64{
		"innovation": d.Innovation, "risk": d.Risk, "hierarchy": d.Hierarchy,
		"accuracy": d.Accuracy, "speed": d.Speed, "transparency": d.Transparency,
		"creativity": d.Creativity, "long_term_thinking": d.LongTermThinking,
		"security": d.Security,
	}
	for k, v := range base {
		delta := math.Mod(seed*7919+float64(len(k))*131, 0.2) - 0.1
		out[k] = clamp(v+delta, 0, 1)
	}
	return out
}

func clamp(v, lo, hi float64) float64 {
	if v < lo {
		return lo
	}
	if v > hi {
		return hi
	}
	return v
}
