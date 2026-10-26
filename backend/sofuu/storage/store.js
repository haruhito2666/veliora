import { newId } from "../core/ids.js";

// A thin document-store wrapper over the Sofuu SDK's structured storage.
//
// Sofuu tables are append-only and strict-schema. Every collection uses the
// same four columns, so a new collection is created automatically from its
// first insert:
//
//   key         stable document key
//   ref         owning organization / investigation, used for list filters
//   payload     JSON document
//   created_at  insertion time
//
// Mutable documents are appended with the same key. Reads take the newest row
// for that key, which gives us event-sourced projections without needing SQL
// updates or deletes.
export class Store {
  constructor(path) {
    this.path = path;
    this.db = sofuu.storage.open(path);
    this.closed = false;
  }

  _insert(collection, key, value, ref) {
    this.db.insert(collection, {
      key: String(key),
      ref: String(ref || ""),
      payload: JSON.stringify(value),
      created_at: Date.now(),
    });
    // The SDK debounces flushes; a first-pass backend writes little enough
    // that flushing each document keeps crash behavior predictable.
    try {
      this.db.flush();
    } catch (_) {
      // A flush failure must not drop the in-memory write.
    }
  }

  put(collection, key, value, ref = "") {
    this._insert(collection, key, value, ref);
    return value;
  }

  append(collection, value, ref = "", key = null) {
    const id = key || value.id || value.event_id || newId("row");
    this._insert(collection, id, value, ref);
    return value;
  }

  get(collection, key) {
    try {
      const rows = this.db.query(collection, {
        filterCol: "key",
        filterVal: String(key),
        limit: 1,
        orderDesc: true,
      });
      if (!rows || rows.length === 0) return null;
      return JSON.parse(rows[0].payload);
    } catch (_) {
      return null;
    }
  }

  list(collection, options = {}) {
    const { ref = null, limit = 10000 } = options;
    try {
      const query = { limit, orderDesc: true };
      if (ref !== null) {
        query.filterCol = "ref";
        query.filterVal = String(ref);
      }
      const rows = this.db.query(collection, query) || [];
      const seen = new Set();
      const out = [];
      for (const row of rows) {
        if (seen.has(row.key)) continue;
        seen.add(row.key);
        try {
          out.push(JSON.parse(row.payload));
        } catch (_) {
          // Ignore a malformed row rather than failing the projection.
        }
      }
      return out;
    } catch (_) {
      return [];
    }
  }

  count(collection, options = {}) {
    return this.list(collection, options).length;
  }

  close() {
    if (this.closed) return;
    try {
      this.db.flush();
      this.db.close();
    } catch (_) {
      // Best effort on shutdown.
    }
    this.closed = true;
  }
}
