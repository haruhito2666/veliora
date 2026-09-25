# Contributing to Veliora

Thanks for helping build Veliora. This is an experimental open-source project; the most useful contributions are focused, explainable improvements to the organization model, runtime, client, and documentation.

## Before you start

- Read [README.md](README.md) and [docs/32-open-source-release.md](docs/32-open-source-release.md).
- Search existing issues and discussions.
- Keep changes scoped to one coherent concern.
- Never include API keys, database dumps, local paths, or personal data in a patch.

## Development setup

```bash
cp .env.example .env
set -a; source .env; set +a
make up
make migrate
bash scripts/run_backend.sh
```

Run tests before submitting:

```bash
make test
cd veliora && ./gradlew :app:assembleDebug
```

The full E2E test requires PostgreSQL, NATS, Redis, and the backend services:

```bash
bash scripts/e2e_golden_path.sh
```

## Contribution guidelines

- Prefer the existing Go service, Python worker, and Compose patterns over new frameworks.
- Preserve the organization-first model: departments, evidence, debate, trust, and institutional memory are the core primitives.
- Keep Android UI changes edge-to-edge, readable, and consistent with the tokens in `veliora/app/src/main/java/com/veliora/ui/theme`.
- Add or update tests for behavior changes. Database/NATS tests may skip when services are unavailable; say so in the report.
- Update the relevant document in `docs/` when changing a public contract or architectural boundary.
- Do not claim that an unfinished investigation path is autonomous or production-ready.

## Commit and PR style

Use small commits with imperative subjects, for example `Remove auth gate from Android startup`. Explain the user-visible behavior, migration impact, and verification commands in the pull request.

## Reporting security issues

Do not open a public issue for a vulnerability. Follow [SECURITY.md](SECURITY.md) and disclose privately to the maintainers.
