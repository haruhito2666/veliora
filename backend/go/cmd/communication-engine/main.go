// Command communication-engine runs the internal Communication Engine (doc 11).
package main

import (
	"context"
	"encoding/json"
	"log"

	"veliora-backend/internal/communication"
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

	svc := communication.NewService(pool)
	b.WireAppenderPublisher(svc)

	// rpc.comm.send
	if err := b.Respond("rpc.comm.send", func(req []byte) (interface{}, error) {
		var r communication.SendRequest
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.Send(ctx, r)
	}); err != nil {
		log.Fatalf("respond send: %v", err)
	}

	// rpc.comm.list
	if err := b.Respond("rpc.comm.list", func(req []byte) (interface{}, error) {
		var r struct {
			OrganizationID  string `json:"organization_id"`
			InvestigationID string `json:"investigation_id,omitempty"`
		}
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.List(ctx, r.OrganizationID, "", r.InvestigationID, "", "", 100)
	}); err != nil {
		log.Fatalf("respond list: %v", err)
	}

	log.Printf("communication-engine listening on NATS (%s)", cfg.NATSURL)
	select {}
}
