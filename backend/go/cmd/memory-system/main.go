// Command memory-system runs the Memory System (doc 06).
package main

import (
	"context"
	"encoding/json"
	"log"

	"veliora-backend/internal/memory"
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

	// Embedder is provided by the Python worker runtime (OpenAI). In this Go
	// service we run with a nil embedder → keyword fallback for recall; the
	// worker runtime calls rpc.memory.create with content only. A future
	// embedder RPC can be wired here.
	svc := memory.NewService(pool, nil)
	b.WireAppenderPublisher(svc)

	// rpc.memory.create
	if err := b.Respond("rpc.memory.create", func(req []byte) (interface{}, error) {
		var r memory.CreateRequest
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.Create(ctx, r)
	}); err != nil {
		log.Fatalf("respond create: %v", err)
	}

	// rpc.memory.recall
	if err := b.Respond("rpc.memory.recall", func(req []byte) (interface{}, error) {
		var r memory.RecallRequest
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.Recall(ctx, r)
	}); err != nil {
		log.Fatalf("respond recall: %v", err)
	}

	// rpc.memory.list
	if err := b.Respond("rpc.memory.list", func(req []byte) (interface{}, error) {
		var r struct {
			OrganizationID string `json:"organization_id"`
			Scope          string `json:"scope,omitempty"`
			DepartmentID   string `json:"department_id,omitempty"`
			WorkerID       string `json:"worker_id,omitempty"`
		}
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.List(ctx, r.OrganizationID, r.Scope, r.DepartmentID, r.WorkerID)
	}); err != nil {
		log.Fatalf("respond list: %v", err)
	}

	log.Printf("memory-system listening on NATS (%s)", cfg.NATSURL)
	select {}
}
