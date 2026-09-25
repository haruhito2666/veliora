# 32 — Open-Source Release Notes

## Release posture

This repository is now staged as an open-source alpha. The source is MIT
licensed, with third-party notices in the root `THIRD_PARTY_NOTICES.md`.

The default product mode is intentionally authless local mode. The Android
client has no sign-in, sign-up, session store, profile identity, or sign-out
flow. The gateway has no auth routes or auth middleware. Anyone who can reach
the gateway can access all organizations in that instance.

## What changed for the open-source release

- Added root README, license, contribution, security, code-of-conduct,
  environment-template, editor, and repository-hygiene files.
- Removed the Android auth/profile/session implementation and DataStore
  dependency.
- Reworked the gateway to expose a health endpoint and authless organization
  routes while retaining organization IDs as data-scoping boundaries.
- Added migration `002_remove_auth.sql` for existing local databases. It drops
  account/session/ownership tables and removes user ownership from
  notifications.
- Removed the nonexistent `debate-engine` process from the startup script and
  corrected the documented command inventory.
- Updated the E2E script to exercise the authless health → organization →
  investigation → event flow.
- Replaced the identity profile with an Instance screen that states the trust
  boundary and shows real organization data.
- Removed all mock/design packages, placeholder-only pages, fabricated mapper
  fallbacks, and local-only actions that did not call the backend.

## Release limitations

This is not a production-ready hosted service. The investigation orchestrator
still stops at the deterministic `researching` plan. Python worker dispatch and
the result handlers that drive evidence, debate, voting, synthesis, report, and
completion are roadmap work. The current Android debug APK and the real-backend
E2E flow both pass.

## Maintainer checklist

1. Run `make test` and `bash scripts/e2e_golden_path.sh` with PostgreSQL, NATS,
   and Redis available.
2. Run `cd veliora && ./gradlew :app:assembleDebug`.
3. Verify no secrets, local properties, build outputs, or virtual environments
   are staged.
4. Publish the repository with the alpha warning visible in the README.
5. Open an issue for the next orchestration milestone rather than presenting the
   deterministic fallback as autonomous completion.
