package bus

import (
	"fmt"
	"testing"
	"time"

	"github.com/nats-io/nats.go"
)

// testBus connects to the local NATS server, skipping if unavailable.
func testBus(t *testing.T) *Bus {
	t.Helper()
	b, err := Connect("nats://localhost:4222")
	if err != nil {
		t.Skipf("NATS not available: %v", err)
	}
	return b
}

func TestOrgStreamPublishSubscribe(t *testing.T) {
	b := testBus(t)
	defer b.Close()

	orgID := "test-org-" + time.Now().Format("150405.000000")
	if err := b.EnsureOrgStream(orgID); err != nil {
		t.Fatalf("ensure stream: %v", err)
	}

	got := make(chan []byte, 1)
	durable := "test-consumer-" + orgID
	subj := fmt.Sprintf("org.%s.>", orgID)
	if err := b.Subscribe(subj, durable, func(m *nats.Msg) {
		got <- m.Data
		_ = m.Ack()
	}); err != nil {
		t.Fatalf("subscribe: %v", err)
	}

	// Give the durable consumer time to attach.
	time.Sleep(500 * time.Millisecond)

	payload := map[string]interface{}{"hello": "world", "n": 1}
	if err := b.PublishJSON(OrgSubject(orgID, "test", "event"), payload); err != nil {
		t.Fatalf("publish: %v", err)
	}

	select {
	case data := <-got:
		if string(data) == "" {
			t.Fatal("empty payload received")
		}
	case <-time.After(3 * time.Second):
		t.Fatal("timed out waiting for event")
	}
}

func TestRequestReply(t *testing.T) {
	b := testBus(t)
	defer b.Close()

	const subject = "rpc.test.echo"
	if err := b.Respond(subject, func(req []byte) (interface{}, error) {
		return string(req), nil
	}); err != nil {
		t.Fatalf("respond: %v", err)
	}

	time.Sleep(200 * time.Millisecond)

	var out string
	if err := b.Request(subject, "ping", &out, 2*time.Second); err != nil {
		t.Fatalf("request: %v", err)
	}
	if out != "ping" {
		t.Fatalf("echo = %q, want ping", out)
	}
}
