// Minimal account registry for authenticated Clerk users.
//
// Clerk is the identity source. This registry deliberately stores only the
// opaque Clerk subject and coarse client metadata needed for a user count and
// support: no email, name, avatar, token, or application content. The subject
// is Clerk's stable `sub` claim, never `sid` (which changes per session).
//
// Sofuu storage is append-only, so a touch appends a new projection with the
// same key. Touches are throttled per user to keep that log small.

import { nowISO } from "../core/ids.js";

const TOUCH_INTERVAL_MS = 15 * 60 * 1000;
const MAX_VERSION_LENGTH = 64;
const PLATFORMS = new Set(["android", "ios", "web", "desktop"]);

function normalizeVersion(raw) {
  const value = String(raw || "").trim();
  return /^[0-9A-Za-z][0-9A-Za-z._+-]*$/.test(value)
    ? value.slice(0, MAX_VERSION_LENGTH)
    : "";
}

function normalizePlatform(raw) {
  const value = String(raw || "").trim().toLowerCase();
  return PLATFORMS.has(value) ? value : "unknown";
}

function present(record) {
  if (!record) return null;
  return {
    user_id: record.user_id,
    first_seen_at: record.first_seen_at,
    last_seen_at: record.last_seen_at,
    app_version: record.app_version || "",
    platform: record.platform || "unknown",
  };
}

export class AccountRegistry {
  constructor(store) {
    this.store = store;
    this.lastTouch = new Map();
  }

  touch(userID, meta = {}) {
    const user_id = String(userID || "").trim();
    if (!user_id) return null;

    const app_version = normalizeVersion(meta.app_version);
    const platform = normalizePlatform(meta.platform);
    const now = Date.now();
    const previous = this.lastTouch.get(user_id);
    if (
      previous &&
      now - previous.at < TOUCH_INTERVAL_MS &&
      previous.app_version === app_version &&
      previous.platform === platform
    ) {
      return present(previous.record);
    }

    const existing = this.store.get("accounts", user_id);
    const record = existing || {
      user_id,
      first_seen_at: nowISO(),
    };
    record.last_seen_at = nowISO();
    if (app_version) record.app_version = app_version;
    if (platform !== "unknown") record.platform = platform;

    this.store.put("accounts", user_id, record, user_id);
    this.lastTouch.set(user_id, { at: now, app_version, platform, record });
    return present(record);
  }

  get(userID) {
    return present(this.store.get("accounts", String(userID || "")));
  }

  count() {
    return this.store.count("accounts");
  }
}
