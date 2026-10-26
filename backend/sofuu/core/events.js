import { newId, nowISO } from "./ids.js";

// In-process event bus plus Server-Sent Events fan-out.
//
// Sofuu's HTTP surface is HTTP/1.1, not WebSocket. The gateway therefore emits
// the same frame shape the Android client already understands, but over SSE:
//
//   { stream, stream_id, seq, event, ts, payload }
//
// Events are also persisted in the `events` collection so the stream has a
// durable audit trail.
export class EventBus {
  constructor(store) {
    this.store = store;
    this.clients = new Set();
    this.sequences = new Map();
    this.heartbeat = setInterval(() => this._heartbeat(), 15000);
  }

  subscribe(orgID, invID, res) {
    res.writeHead(200, {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      "Connection": "keep-alive",
      "X-Accel-Buffering": "no",
      "Access-Control-Allow-Origin": "*",
    });
    res.write("retry: 3000\n\n");
    res.write(`: connected ${nowISO()}\n\n`);

    const client = { orgID, invID: invID || null, res };
    this.clients.add(client);
    return client;
  }

  unsubscribe(client) {
    this.clients.delete(client);
  }

  publish(orgID, eventType, payload, options = {}) {
    const stream = options.stream || "organization";
    const streamID = options.streamID || orgID;
    const seq = (this.sequences.get(orgID) || 0) + 1;
    this.sequences.set(orgID, seq);

    const envelope = {
      event_id: newId("evt"),
      organization_id: orgID,
      event_type: eventType,
      stream,
      stream_id: streamID,
      seq,
      ts: nowISO(),
      payload: payload || {},
    };

    this.store.put("events", envelope.event_id, envelope, orgID);

    const frame = {
      stream,
      stream_id: streamID,
      seq,
      event: eventType,
      ts: envelope.ts,
      payload: envelope.payload,
    };
    const wire = `data: ${JSON.stringify(frame)}\n\n`;

    for (const client of Array.from(this.clients)) {
      if (client.orgID !== orgID) continue;
      if (client.invID && payload && payload.investigation_id &&
          payload.investigation_id !== client.invID) {
        continue;
      }
      try {
        client.res.write(wire);
      } catch (_) {
        this.clients.delete(client);
      }
    }
    return envelope;
  }

  _heartbeat() {
    for (const client of Array.from(this.clients)) {
      try {
        client.res.write(`: heartbeat ${nowISO()}\n\n`);
      } catch (_) {
        this.clients.delete(client);
      }
    }
  }

  close() {
    clearInterval(this.heartbeat);
    for (const client of Array.from(this.clients)) {
      try {
        client.res.end();
      } catch (_) {
        // Already gone.
      }
    }
    this.clients.clear();
  }
}
