// Command org-manager runs the Organization Manager service (doc 04 §3.1).
package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"path/filepath"

	"veliora-backend/internal/orgmgr"
	"veliora-backend/internal/shared/bus"
	"veliora-backend/internal/shared/config"
	"veliora-backend/internal/shared/contracts"
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

	templateDir := os.Getenv("TEMPLATES_DIR")
	if templateDir == "" {
		// Resolve repo-root templates regardless of CWD.
		if dir, err := repoRoot("templates/organizations"); err == nil {
			templateDir = dir
		} else {
			templateDir = "templates/organizations"
		}
	}
	svc := orgmgr.NewService(pool, templateDir)
	b.WireAppenderPublisher(svc)

	// rpc.orgmgr.create
	if err := b.Respond("rpc.orgmgr.create", func(req []byte) (interface{}, error) {
		var r contracts.CreateOrgRequest
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.CreateOrg(ctx, r)
	}); err != nil {
		log.Fatalf("respond create: %v", err)
	}

	// rpc.orgmgr.get
	if err := b.Respond("rpc.orgmgr.get", func(req []byte) (interface{}, error) {
		var r contracts.GetOrgRequest
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.GetOrgSnapshot(ctx, r.OrganizationID)
	}); err != nil {
		log.Fatalf("respond get: %v", err)
	}

	// rpc.orgmgr.status
	if err := b.Respond("rpc.orgmgr.status", func(req []byte) (interface{}, error) {
		var r contracts.GetOrgRequest
		if err := json.Unmarshal(req, &r); err != nil {
			return nil, err
		}
		return svc.GetOrgStatus(ctx, r.OrganizationID)
	}); err != nil {
		log.Fatalf("respond status: %v", err)
	}

	log.Printf("org-manager listening on NATS (%s)", cfg.NATSURL)
	select {}
}

// repoRoot walks up from CWD to find the repo root containing rel.
func repoRoot(rel string) (string, error) {
	dir, err := os.Getwd()
	if err != nil {
		return "", err
	}
	for {
		if _, err := os.Stat(filepath.Join(dir, rel)); err == nil {
			return filepath.Join(dir, rel), nil
		}
		parent := filepath.Dir(dir)
		if parent == dir {
			return "", fmt.Errorf("not found")
		}
		dir = parent
	}
}
