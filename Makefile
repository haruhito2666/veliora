# Veliora backend monorepo — Docker + Make orchestration.

SHELL := /bin/bash
.PHONY: up down migrate run run-% test e2e build psql nats status

# ── Stack (native, no Docker) ──────────────────────────────────────────
up:
	scripts/services.sh start

down:
	scripts/services.sh stop

status:
	scripts/services.sh status

psql:
	psql -h 127.0.0.1 -U veliora -d veliora

nats:
	nats --server=nats://localhost:4222 $(filter-out $@,$(MAKECMDGOALS))

# ── Migrations ───────────────────────────────────────────────────────
migrate:
	cd backend/go && go run ./cmd/migrate

# ── Run a service ────────────────────────────────────────────────────
# make run-api-gateway
run-%:
	cd backend/go && go run ./cmd/$*

# ── Tests ────────────────────────────────────────────────────────────
test:
	cd backend/go && go test ./...
	cd backend/workers && uv run pytest -q

e2e:
	bash scripts/e2e_golden_path.sh

build:
	cd backend/go && go build ./...
