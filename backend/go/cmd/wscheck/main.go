// Command wscheck is a tiny e2e helper: connects to the org WebSocket and
// prints investigation events. Used by scripts/e2e_golden_path.sh.
package main

import (
	"context"
	"flag"
	"log"
	"time"

	"github.com/nats-io/nats.go"

	"veliora-backend/internal/shared/bus"
)

func main() {
	orgID := flag.String("org", "", "organization id")
	flag.Parse()

	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()

	b, err := bus.Connect("nats://localhost:4222")
	if err != nil {
		log.Fatalf("nats: %v", err)
	}
	defer b.Close()

	if err := b.EnsureOrgStream(*orgID); err != nil {
		log.Fatalf("stream: %v", err)
	}

	// Durable consumer that prints investigation.* events as they land.
	if err := b.Subscribe("org."+*orgID+".investigation.>", "wscheck-"+*orgID,
		func(m *nats.Msg) {
			log.Printf("investigation event: %s", string(m.Data))
			_ = m.Ack()
		}); err != nil {
		log.Fatalf("subscribe: %v", err)
	}

	<-ctx.Done()
}
