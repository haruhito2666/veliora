// Command api-gateway runs the public API surface (doc 24).
package main

import (
	"context"
	"log"
	"net/http"

	"veliora-backend/internal/gateway"
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

	srv := gateway.NewServer(pool, b)
	addr := ":" + cfg.Port
	log.Printf("api-gateway listening on %s", addr)
	if err := http.ListenAndServe(addr, srv.Router()); err != nil {
		log.Fatalf("serve: %v", err)
	}
}
