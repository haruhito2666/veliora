// Command report-engine assembles board reports (doc 19 §10).
package main

import (
	"context"
	"encoding/json"
	"log"

	"veliora-backend/internal/report"
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

	svc := report.NewService(pool)

	// rpc.report.get
	if err := b.Respond("rpc.report.get", func(req []byte) (interface{}, error) {
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

	log.Printf("report-engine listening on NATS (%s)", cfg.NATSURL)
	select {}
}
