// Command meeting-engine runs the Meeting Engine (doc 20).
package main

import (
	"context"
	"encoding/json"
	"log"

	"veliora-backend/internal/meeting"
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

	svc := meeting.NewService(pool)
	b.WireAppenderPublisher(svc)

	// rpc.meeting.list
	if err := b.Respond("rpc.meeting.list", func(req []byte) (interface{}, error) {
		var r struct {
			OrganizationID string `json:"organization_id"`
		}
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.List(ctx, r.OrganizationID, "")
	}); err != nil {
		log.Fatalf("respond list: %v", err)
	}

	// rpc.meeting.get
	if err := b.Respond("rpc.meeting.get", func(req []byte) (interface{}, error) {
		var r struct {
			OrganizationID string `json:"organization_id"`
			MeetingID      string `json:"meeting_id"`
		}
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.Get(ctx, r.OrganizationID, r.MeetingID)
	}); err != nil {
		log.Fatalf("respond get: %v", err)
	}

	// rpc.meeting.call
	if err := b.Respond("rpc.meeting.call", func(req []byte) (interface{}, error) {
		var r meeting.CallRequest
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.Call(ctx, r)
	}); err != nil {
		log.Fatalf("respond call: %v", err)
	}

	log.Printf("meeting-engine listening on NATS (%s)", cfg.NATSURL)
	select {}
}
