// Command dna-engine runs the DNA Engine (doc 21).
package main

import (
	"context"
	"encoding/json"
	"log"

	"veliora-backend/internal/dna"
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

	svc := dna.NewService(pool)

	// rpc.dna.get
	if err := b.Respond("rpc.dna.get", func(req []byte) (interface{}, error) {
		var r struct {
			OrganizationID string `json:"organization_id"`
		}
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.GetCurrent(ctx, r.OrganizationID)
	}); err != nil {
		log.Fatalf("respond get: %v", err)
	}

	// rpc.dna.derive_debate_params
	if err := b.Respond("rpc.dna.derive_debate_params", func(req []byte) (interface{}, error) {
		var r struct {
			OrganizationID string `json:"organization_id"`
		}
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		d, err := svc.GetCurrent(ctx, r.OrganizationID)
		if err != nil {
			return nil, err
		}
		return dna.DeriveDebateParams(d), nil
	}); err != nil {
		log.Fatalf("respond derive: %v", err)
	}

	log.Printf("dna-engine listening on NATS (%s)", cfg.NATSURL)
	select {}
}
