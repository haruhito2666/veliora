-- Veliora authless-local migration.
-- WARNING: this permanently removes account and session data. Export any data
-- you need before applying it. Veliora is intended only for trusted instances.
ALTER TABLE notifications DROP COLUMN IF EXISTS user_id;
DROP INDEX IF EXISTS notif_user_idx;
CREATE INDEX IF NOT EXISTS notif_org_idx ON notifications(organization_id, created_at);
DROP TABLE IF EXISTS user_orgs;
DROP TABLE IF EXISTS sessions;
DROP TABLE IF EXISTS users;
