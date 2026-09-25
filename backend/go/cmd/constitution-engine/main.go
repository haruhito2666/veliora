// Command constitution-engine runs the Constitution Engine (doc 09).
package main

import (
	"context"
	"encoding/json"
	"log"

	"veliora-backend/internal/constitution"
	"veliora-backend/internal/shared/bus"
	"veliora-backend/internal/shared/config"
	"veliora-backend/internal/shared/db"
)

func main() {
	ctx := context.Background()
	cfg := config.FromEnv()

	pool, err := db.Connect(ctx, cfg.PostgresURL)
	if err != nil {
		log.Fatalf("db: %v", err)
	}
	defer pool.Close()

	b, err := bus.Connect(cfg.NATSURL)
	if err != nil {
		log.Fatalf("nats: %v", err)
	}
	defer b.Close()

	svc := constitution.NewService(pool)
	b.WireAppenderPublisher(svc)

	// rpc.constitution.get
	if err := b.Respond("rpc.constitution.get", func(req []byte) (interface{}, error) {
		var r constitution.CheckRequest
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.GetCurrent(ctx, r.OrganizationID)
	}); err != nil {
		log.Fatalf("respond get: %v", err)
	}

	// rpc.constitution.check
	if err := b.Respond("rpc.constitution.check", func(req []byte) (interface{}, error) {
		var r constitution.CheckRequest
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.Check(ctx, r)
	}); err != nil {
		log.Fatalf("respond check: %v", err)
	}

	// rpc.constitution.propose_amendment
	if err := b.Respond("rpc.constitution.propose_amendment", func(req []byte) (interface{}, error) {
		var r constitution.ProposeAmendmentRequest
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		id, err := svc.ProposeAmendment(ctx, r)
		if err != nil {
			return nil, err
		}
		return map[string]string{"amendment_id": id}, nil
	}); err != nil {
		log.Fatalf("respond propose: %v", err)
	}

	// rpc.constitution.apply_amendment
	if err := b.Respond("rpc.constitution.apply_amendment", func(req []byte) (interface{}, error) {
		var r struct {
			OrganizationID string `json:"organization_id"`
			AmendmentID    string `json:"amendment_id"`
		}
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		if err := svc.ApplyAmendment(ctx, r.OrganizationID, r.AmendmentID); err != nil {
			return nil, err
		}
		return map[string]bool{"applied": true}, nil
	}); err != nil {
		log.Fatalf("respond apply: %v", err)
	}

	// rpc.constitution.history
	if err := b.Respond("rpc.constitution.history", func(req []byte) (interface{}, error) {
		var r constitution.CheckRequest
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.History(ctx, r.OrganizationID)
	}); err != nil {
		log.Fatalf("respond history: %v", err)
	}

	log.Printf("constitution-engine listening on NATS (%s)", cfg.NATSURL)
	select {}
}
