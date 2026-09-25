// Package bus wraps NATS JetStream for the Veliora event bus (doc 04 §8.3,
// doc 16 §16.6): per-org ordered streams, durable consumers, and
// request/reply RPC.
package bus

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"
	"time"

	"github.com/nats-io/nats.go"

	"veliora-backend/internal/shared/events"
)

// StreamConfig names the JetStream pieces.
const (
	// StreamPrefix is the JetStream stream per organization.
	StreamPrefix = "ORG"
	// SubjectPrefix is the NATS subject space for org events.
	SubjectPrefix = "org"
	// RPCSubjectPrefix is the request/reply subject space.
	RPCSubjectPrefix = "rpc"
	// TaskSubjectPrefix is the worker task queue subject space.
	TaskSubjectPrefix = "org"
)

// Bus is a connected NATS JetStream client.
type Bus struct {
	nc *nats.Conn
	js nats.JetStreamContext
}

// Connect dials NATS and returns a Bus.
func Connect(url string) (*Bus, error) {
	nc, err := nats.Connect(url,
		nats.MaxReconnects(-1),
		nats.ReconnectWait(2*time.Second),
		nats.Timeout(5*time.Second))
	if err != nil {
		return nil, fmt.Errorf("nats connect: %w", err)
	}
	js, err := nc.JetStream()
	if err != nil {
		nc.Close()
		return nil, fmt.Errorf("jetstream: %w", err)
	}
	return &Bus{nc: nc, js: js}, nil
}

// Close disconnects.
func (b *Bus) Close() {
	b.nc.Close()
}

// Raw exposes the underlying connection (for advanced use).
func (b *Bus) Raw() *nats.Conn { return b.nc }

// OrgSubject builds the event subject for an org (doc 04 §8.3):
// org.<org_id>.<topic>.<verb>
func OrgSubject(orgID, topic, verb string) string {
	return fmt.Sprintf("%s.%s.%s.%s", SubjectPrefix, orgID, topic, verb)
}

// EnsureOrgStream creates (if absent) the org's JetStream stream with
// wildcard subjects org.<org_id>.> and at-least-once delivery.
func (b *Bus) EnsureOrgStream(orgID string) error {
	stream := StreamName(orgID)
	_, err := b.js.AddStream(&nats.StreamConfig{
		Name:      stream,
		Subjects:  []string{fmt.Sprintf("org.%s.>", orgID)},
		Storage:   nats.FileStorage,
		Retention: nats.LimitsPolicy,
		MaxAge:    7 * 24 * time.Hour,
	})
	if err != nil && err != nats.ErrStreamNameAlreadyInUse {
		return fmt.Errorf("add stream %s: %w", stream, err)
	}
	return nil
}

// StreamName builds a NATS-safe stream name from an org id (UUIDs contain
// dashes, which are allowed; dots are not).
func StreamName(orgID string) string {
	replacer := strings.NewReplacer(".", "-")
	return StreamPrefix + "_" + replacer.Replace(orgID)
}

// PublishJSON publishes an event envelope (or any JSON-marshalable payload)
// to a subject, at-least-once via JetStream.
func (b *Bus) PublishJSON(subject string, v interface{}) error {
	data, err := json.Marshal(v)
	if err != nil {
		return err
	}
	_, err = b.js.Publish(subject, data)
	return err
}

// PublishEnvelope publishes an event envelope to its org stream.
func (b *Bus) PublishEnvelope(subject string, ev interface{}) error {
	return b.PublishJSON(subject, ev)
}

// WireAppenderPublisher connects an events.Appender to this bus so appended
// events fan out to JetStream. Keeps the wiring in one place for all services.
func (b *Bus) WireAppenderPublisher(app interface {
	SetEventPublisher(p events.Publisher)
}) {
	app.SetEventPublisher(b)
}

// Subscribe is a durable, ordered push subscription to a subject pattern.
// handler receives the raw message; ack it via msg.Ack().
func (b *Bus) Subscribe(subject, durable string, handler func(msg *nats.Msg)) error {
	// NATS durable consumer names cannot contain dots.
	durable = strings.NewReplacer(".", "-").Replace(durable)
	_, err := b.js.Subscribe(subject, handler,
		nats.Durable(durable),
		nats.ManualAck(),
		nats.MaxDeliver(10),
		nats.AckWait(30*time.Second),
	)
	if err != nil {
		return fmt.Errorf("subscribe %s: %w", subject, err)
	}
	return nil
}

// Request performs a request/reply RPC with a timeout. req is JSON-marshaled;
// resp is JSON-unmarshaled into out.
func (b *Bus) Request(subject string, req, out interface{}, timeout time.Duration) error {
	data, err := json.Marshal(req)
	if err != nil {
		return err
	}
	msg, err := b.nc.Request(subject, data, timeout)
	if err != nil {
		return fmt.Errorf("rpc %s: %w", subject, err)
	}
	if out == nil {
		return nil
	}
	return json.Unmarshal(msg.Data, out)
}

// Respond registers a request/reply handler on a subject. handler takes the
// decoded request and returns the response or an error. If the handler
// returns a string or []byte, it is passed through as-is (already-encoded);
// anything else is JSON-marshaled.
func (b *Bus) Respond(subject string, handler func(req []byte) (interface{}, error)) error {
	_, err := b.nc.Subscribe(subject, func(msg *nats.Msg) {
		resp, err := handler(msg.Data)
		if err != nil {
			_ = msg.Respond([]byte(`{"error":"` + jsonEscape(err.Error()) + `"}`))
			return
		}
		var data []byte
		switch v := resp.(type) {
		case string:
			data = []byte(v)
		case []byte:
			data = v
		default:
			data, err = json.Marshal(v)
			if err != nil {
				_ = msg.Respond([]byte(`{"error":"encode"}`))
				return
			}
		}
		_ = msg.Respond(data)
	})
	if err != nil {
		return fmt.Errorf("respond %s: %w", subject, err)
	}
	return nil
}

// QueueSubscribe creates a queue-group subscription for worker task
// distribution: org.<org_id>.task.<task_type> with queue group per (org,dept).
func (b *Bus) QueueSubscribe(subject, queue string, handler func(msg *nats.Msg)) error {
	_, err := b.nc.QueueSubscribe(subject, queue, handler)
	if err != nil {
		return fmt.Errorf("queue subscribe %s: %w", subject, err)
	}
	return nil
}

// PublishRaw publishes raw bytes to a subject.
func (b *Bus) PublishRaw(subject string, data []byte) error {
	return b.nc.Publish(subject, data)
}

func jsonEscape(s string) string {
	b, _ := json.Marshal(s)
	return string(b)
}

var _ = context.Background
