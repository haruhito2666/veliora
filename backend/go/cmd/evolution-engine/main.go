// Command evolution-engine runs the Evolution Engine (doc 07).
package main

import (
	"context"
	"encoding/json"
	"log"

	"veliora-backend/internal/evolution"
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

	svc := evolution.NewService(pool)
	b.WireAppenderPublisher(svc)

	// rpc.evolution.list
	if err := b.Respond("rpc.evolution.list", func(req []byte) (interface{}, error) {
		var r struct {
			OrganizationID string `json:"organization_id"`
		}
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.List(ctx, r.OrganizationID)
	}); err != nil {
		log.Fatalf("respond list: %v", err)
	}

	// rpc.evolution.propose
	if err := b.Respond("rpc.evolution.propose", func(req []byte) (interface{}, error) {
		var r evolution.ProposeRequest
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.Propose(ctx, r)
	}); err != nil {
		log.Fatalf("respond propose: %v", err)
	}

	// rpc.evolution.auto
	if err := b.Respond("rpc.evolution.auto", func(req []byte) (interface{}, error) {
		var r struct {
			OrganizationID string `json:"organization_id"`
		}
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		created, err := svc.AutoProposals(ctx, r.OrganizationID)
		if err != nil {
			return nil, err
		}
		return map[string]interface{}{"created": created}, nil
	}); err != nil {
		log.Fatalf("respond auto: %v", err)
	}

	log.Printf("evolution-engine listening on NATS (%s)", cfg.NATSURL)
	select {}
}
