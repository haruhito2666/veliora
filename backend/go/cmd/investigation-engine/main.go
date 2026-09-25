// Command investigation-engine runs the Investigation Engine (doc 19).
package main

import (
	"context"
	"encoding/json"
	"log"

	"veliora-backend/internal/investigation"
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

	svc := investigation.NewService(pool)
	b.WireAppenderPublisher(svc)

	// rpc.investigation.create
	if err := b.Respond("rpc.investigation.create", func(req []byte) (interface{}, error) {
		var r struct {
			OrganizationID string `json:"organization_id"`
			Prompt         string `json:"prompt"`
			Depth          string `json:"depth"`
		}
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		inv, err := svc.Create(ctx, investigation.CreateRequest{
			OrganizationID: r.OrganizationID, Prompt: r.Prompt, Depth: r.Depth,
		})
		if err != nil {
			return nil, err
		}
		// Kick off planning immediately (fallback plan until worker runtime).
		if err := svc.StartPlanning(ctx, r.OrganizationID, inv.InvestigationID, investigation.CreateRequest{
			OrganizationID: r.OrganizationID, Prompt: r.Prompt,
		}); err != nil {
			log.Printf("planning: %v", err)
		}
		return inv, nil
	}); err != nil {
		log.Fatalf("respond create: %v", err)
	}

	// rpc.investigation.get
	if err := b.Respond("rpc.investigation.get", func(req []byte) (interface{}, error) {
		var r struct {
			OrganizationID  string `json:"organization_id"`
			InvestigationID string `json:"investigation_id"`
		}
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.Get(ctx, r.OrganizationID, r.InvestigationID)
	}); err != nil {
		log.Fatalf("respond get: %v", err)
	}

	// rpc.investigation.list
	if err := b.Respond("rpc.investigation.list", func(req []byte) (interface{}, error) {
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

	// rpc.investigation.cancel
	if err := b.Respond("rpc.investigation.cancel", func(req []byte) (interface{}, error) {
		var r struct {
			OrganizationID  string `json:"organization_id"`
			InvestigationID string `json:"investigation_id"`
		}
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		if err := svc.Cancel(ctx, r.OrganizationID, r.InvestigationID); err != nil {
			return nil, err
		}
		return map[string]bool{"cancelled": true}, nil
	}); err != nil {
		log.Fatalf("respond cancel: %v", err)
	}

	log.Printf("investigation-engine listening on NATS (%s)", cfg.NATSURL)
	select {}
}
