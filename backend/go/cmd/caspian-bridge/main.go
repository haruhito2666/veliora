// Command caspian-bridge runs the Caspian Bridge (doc 12).
package main

import (
	"context"
	"encoding/json"
	"log"

	"veliora-backend/internal/caspian"
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

	svc := caspian.NewService(pool, caspian.FromEnv())
	b.WireAppenderPublisher(svc)

	// rpc.caspian.threads
	if err := b.Respond("rpc.caspian.threads", func(req []byte) (interface{}, error) {
		var r struct {
			OrganizationID string `json:"organization_id"`
		}
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.Threads(ctx, r.OrganizationID)
	}); err != nil {
		log.Fatalf("respond threads: %v", err)
	}

	// rpc.caspian.send
	if err := b.Respond("rpc.caspian.send", func(req []byte) (interface{}, error) {
		var r caspian.SendRequest
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.Send(ctx, r)
	}); err != nil {
		log.Fatalf("respond send: %v", err)
	}

	// rpc.caspian.add_contact
	if err := b.Respond("rpc.caspian.add_contact", func(req []byte) (interface{}, error) {
		var r caspian.Contact
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		if err := svc.AddContact(ctx, r); err != nil {
			return nil, err
		}
		return map[string]bool{"ok": true}, nil
	}); err != nil {
		log.Fatalf("respond add_contact: %v", err)
	}

	log.Printf("caspian-bridge listening on NATS (%s)", cfg.NATSURL)
	select {}
}
