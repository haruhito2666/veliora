// Command knowledge-graph runs the Knowledge Graph engine (doc 14).
package main

import (
	"context"
	"encoding/json"
	"log"

	"veliora-backend/internal/kgraph"
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

	svc := kgraph.NewService(pool)
	b.WireAppenderPublisher(svc)

	// rpc.kg.add_claim
	if err := b.Respond("rpc.kg.add_claim", func(req []byte) (interface{}, error) {
		var r kgraph.AddClaimRequest
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.AddClaim(ctx, r)
	}); err != nil {
		log.Fatalf("respond add_claim: %v", err)
	}

	// rpc.kg.add_edge
	if err := b.Respond("rpc.kg.add_edge", func(req []byte) (interface{}, error) {
		var r kgraph.AddEdgeRequest
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.AddEdge(ctx, r)
	}); err != nil {
		log.Fatalf("respond add_edge: %v", err)
	}

	// rpc.kg.subgraph
	if err := b.Respond("rpc.kg.subgraph", func(req []byte) (interface{}, error) {
		var r struct {
			OrganizationID  string `json:"organization_id"`
			InvestigationID string `json:"investigation_id"`
		}
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.SubgraphOf(ctx, r.OrganizationID, r.InvestigationID)
	}); err != nil {
		log.Fatalf("respond subgraph: %v", err)
	}

	// rpc.kg.set_relationship
	if err := b.Respond("rpc.kg.set_relationship", func(req []byte) (interface{}, error) {
		var r struct {
			OrganizationID string `json:"organization_id"`
			kgraph.Relationship
		}
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		if err := svc.SetRelationship(ctx, r.OrganizationID, r.Relationship); err != nil {
			return nil, err
		}
		return map[string]bool{"ok": true}, nil
	}); err != nil {
		log.Fatalf("respond set_relationship: %v", err)
	}

	// rpc.kg.relationships
	if err := b.Respond("rpc.kg.relationships", func(req []byte) (interface{}, error) {
		var r struct {
			OrganizationID string `json:"organization_id"`
			WorkerID       string `json:"worker_id"`
		}
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.RelationshipsOf(ctx, r.OrganizationID, r.WorkerID)
	}); err != nil {
		log.Fatalf("respond relationships: %v", err)
	}

	log.Printf("knowledge-graph listening on NATS (%s)", cfg.NATSURL)
	select {}
}
