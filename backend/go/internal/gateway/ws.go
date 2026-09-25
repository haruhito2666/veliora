package gateway

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"sync"
	"time"

	"github.com/gorilla/websocket"
	"github.com/nats-io/nats.go"

	"veliora-backend/internal/shared/bus"
)

// WSHub fans out NATS org event streams to WebSocket clients (doc 24 §10).
type WSHub struct {
	bus  *bus.Bus
	up   websocket.Upgrader
	mu   sync.Mutex
	subs map[string]*wsSub
}

// wsSub is one org-scoped NATS subscription fanning to its clients.
type wsSub struct {
	clients map[*wsClient]bool
}

type wsClient struct {
	conn *websocket.Conn
	seq  int64
}

// NewWSHub builds the hub.
func NewWSHub(b *bus.Bus) *WSHub {
	return &WSHub{
		bus: b,
		up: websocket.Upgrader{
			CheckOrigin: func(r *http.Request) bool { return true },
		},
		subs: map[string]*wsSub{},
	}
}

// frame is the wire envelope (doc 24 §10.1).
type frame struct {
	Stream   string      `json:"stream"`
	StreamID string      `json:"stream_id"`
	Seq      int64       `json:"seq"`
	Event    string      `json:"event"`
	TS       string      `json:"ts"`
	Payload  interface{} `json:"payload"`
}

// ServeOrg streams all events for an org.
func (h *WSHub) ServeOrg(w http.ResponseWriter, r *http.Request, orgID string) {
	h.serve(w, r, "organization", orgID, fmt.Sprintf("org.%s.>", orgID))
}

// ServeInvestigation streams all events for an investigation (org stream
// filtered server-side by payload correlation).
func (h *WSHub) ServeInvestigation(w http.ResponseWriter, r *http.Request, orgID, invID string) {
	h.serve(w, r, "investigation", invID, fmt.Sprintf("org.%s.>", orgID))
}

func (h *WSHub) serve(w http.ResponseWriter, r *http.Request, stream, streamID, subject string) {
	conn, err := h.up.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("ws upgrade failed: %v", err)
		return
	}
	log.Printf("ws upgraded: stream=%s id=%s", stream, streamID)
	client := &wsClient{conn: conn}
	key := streamID

	// Ensure the org's JetStream stream exists before subscribing.
	if err := h.bus.EnsureOrgStream(streamID); err != nil {
		log.Printf("ws ensure stream failed: %v", err)
		_ = conn.Close()
		return
	}

	h.mu.Lock()
	sub, ok := h.subs[key]
	if !ok {
		sub = &wsSub{clients: map[*wsClient]bool{}}
		// Subscribe once per stream key; fan out to clients.
		durable := "ws-" + streamID
		if err := h.bus.Subscribe(subject, durable, func(msg *nats.Msg) {
			h.fanout(key, msg.Data)
		}); err != nil {
			log.Printf("ws subscribe failed: %v", err)
			h.mu.Unlock()
			_ = conn.Close()
			return
		}
		h.subs[key] = sub
	}
	sub.clients[client] = true
	h.mu.Unlock()

	// Read pump: detects client disconnect / inbound frames.
	readDone := make(chan struct{})
	go func() {
		defer close(readDone)
		for {
			if _, _, err := conn.NextReader(); err != nil {
				log.Printf("ws read end: %v", err)
				return
			}
		}
	}()

	// Heartbeat every 5s (doc 24 §10.4).
	ticker := time.NewTicker(5 * time.Second)
	defer ticker.Stop()
	defer func() {
		h.mu.Lock()
		if sub, ok := h.subs[key]; ok {
			delete(sub.clients, client)
		}
		h.mu.Unlock()
		_ = conn.Close()
	}()
	for {
		select {
		case <-ticker.C:
			if err := conn.WriteJSON(frame{Stream: stream, StreamID: streamID, Seq: client.seq, Event: "stream.heartbeat", TS: timeNow(), Payload: map[string]string{}}); err != nil {
				return
			}
		case <-readDone:
			return
		}
	}
}

func (h *WSHub) fanout(key string, data []byte) {
	var env struct {
		EventType string    `json:"event_type"`
		Timestamp time.Time `json:"timestamp"`
		Payload   interface{} `json:"payload"`
	}
	if err := json.Unmarshal(data, &env); err != nil {
		return
	}
	h.mu.Lock()
	sub, ok := h.subs[key]
	if !ok {
		h.mu.Unlock()
		return
	}
	for c := range sub.clients {
		c.seq++
		f := frame{
			Stream: key, StreamID: key, Seq: c.seq,
			Event: env.EventType, TS: env.Timestamp.Format(time.RFC3339Nano),
			Payload: env.Payload,
		}
		if err := c.conn.WriteJSON(f); err != nil {
			delete(sub.clients, c)
		}
	}
	h.mu.Unlock()
}

func timeNow() string { return time.Now().UTC().Format(time.RFC3339Nano) }
