// Command trust-engine runs the Trust Engine (doc 08).
package main

import (
	"context"
	"encoding/json"
	"log"

	"veliora-backend/internal/shared/bus"
	"veliora-backend/internal/shared/config"
	"veliora-backend/internal/shared/db"
	"veliora-backend/internal/trust"
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

	svc := trust.NewService(pool)
	b.WireAppenderPublisher(svc)

	// rpc.trust.get
	if err := b.Respond("rpc.trust.get", func(req []byte) (interface{}, error) {
		var r trust.WeightRequest
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.Get(ctx, r.OrganizationID, r.WorkerID)
	}); err != nil {
		log.Fatalf("respond get: %v", err)
	}

	// rpc.trust.weight
	if err := b.Respond("rpc.trust.weight", func(req []byte) (interface{}, error) {
		var r trust.WeightRequest
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.Weight(ctx, r)
	}); err != nil {
		log.Fatalf("respond weight: %v", err)
	}

	// rpc.trust.update
	if err := b.Respond("rpc.trust.update", func(req []byte) (interface{}, error) {
		var r trust.UpdateRequest
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.Update(ctx, r)
	}); err != nil {
		log.Fatalf("respond update: %v", err)
	}

	// rpc.trust.history
	if err := b.Respond("rpc.trust.history", func(req []byte) (interface{}, error) {
		var r struct {
			OrganizationID string `json:"organization_id"`
			WorkerID       string `json:"worker_id"`
		}
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.History(ctx, r.OrganizationID, r.WorkerID)
	}); err != nil {
		log.Fatalf("respond history: %v", err)
	}

	log.Printf("trust-engine listening on NATS (%s)", cfg.NATSURL)
	select {}
}
