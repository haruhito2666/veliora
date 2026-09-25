#!/bin/bash
# Veliora local dev services — no Docker.
# Runs PostgreSQL 17 (+pgvector), Redis, and NATS JetStream natively via brew.

set -euo pipefail

# macOS shells can launch with an empty locale, which PostgreSQL rejects at
# startup. Prefer an explicit UTF-8 locale while preserving caller settings.
if [ -z "${LC_ALL:-}" ] && [ -z "${LANG:-}" ]; then
  if locale -a 2>/dev/null | grep -qx 'C.UTF-8'; then
    export LC_ALL=C.UTF-8
  else
    export LC_ALL=en_US.UTF-8
  fi
fi

BREW_PREFIX="$(brew --prefix)"
PG_BIN="$BREW_PREFIX/opt/postgresql@17/bin"
PGDATA="$HOME/.veliora/pgdata"
PGLOG="$HOME/.veliora/pg.log"
REDIS_PID="$HOME/.veliora/redis.pid"
NATS_PID="$HOME/.veliora/nats.pid"

mkdir -p "$HOME/.veliora"

start_postgres() {
  if "$PG_BIN/pg_ctl" -D "$PGDATA" status >/dev/null 2>&1; then
    echo "postgres: already running"
    return
  fi
  if [ ! -f "$PGDATA/PG_VERSION" ]; then
    echo "postgres: initializing data dir at $PGDATA"
    "$PG_BIN/initdb" -D "$PGDATA" -U veliora --auth=trust >/dev/null
    # Bind localhost only.
    cat >> "$PGDATA/postgresql.conf" <<'CONF'
listen_addresses = '127.0.0.1'
port = 5432
CONF
    # pgvector extension dir (brew installs it into pg's share).
    PGVECTOR_SHARE="$BREW_PREFIX/opt/pgvector/share/postgresql@17/extension"
    if [ -d "$PGVECTOR_SHARE" ] && [ ! -e "$PGDATA/extensions" ]; then
      ln -s "$PGVECTOR_SHARE" "$PGDATA/extensions"
    fi
  fi
  "$PG_BIN/pg_ctl" -D "$PGDATA" -l "$PGLOG" start
  sleep 2
  # Create role + database.
  "$PG_BIN/psql" -h 127.0.0.1 -U veliora -d postgres -tc \
    "SELECT 1 FROM pg_roles WHERE rolname='veliora'" | grep -q 1 || \
    "$PG_BIN/psql" -h 127.0.0.1 -U veliora -d postgres -c \
    "CREATE ROLE veliora LOGIN SUPERUSER PASSWORD 'veliora';"
  "$PG_BIN/psql" -h 127.0.0.1 -U veliora -d postgres -tc \
    "SELECT 1 FROM pg_database WHERE datname='veliora'" | grep -q 1 || \
    "$PG_BIN/psql" -h 127.0.0.1 -U veliora -d postgres -c \
    "CREATE DATABASE veliora OWNER veliora;"
  echo "postgres: running on 127.0.0.1:5432 (db=veliora, user=veliora)"
}

start_redis() {
  if [ -f "$REDIS_PID" ] && kill -0 "$(cat "$REDIS_PID")" 2>/dev/null; then
    echo "redis: already running"
    return
  fi
  redis-server --port 6379 --daemonize yes --pidfile "$REDIS_PID" \
    --dir "$HOME/.veliora" --dbfilename redis.rdb --logfile "$HOME/.veliora/redis.log"
  echo "redis: running on 127.0.0.1:6379"
}

start_nats() {
  if [ -f "$NATS_PID" ] && kill -0 "$(cat "$NATS_PID")" 2>/dev/null; then
    echo "nats: already running"
    return
  fi
  nats-server -js -p 4222 -m 8222 -sd "$HOME/.veliora/nats" \
    -P "$NATS_PID" -l "$HOME/.veliora/nats.log" >/dev/null 2>&1 &
  echo "nats: running on 127.0.0.1:4222 (monitor 8222)"
}

stop_all() {
  echo "stopping services..."
  "$PG_BIN/pg_ctl" -D "$PGDATA" stop >/dev/null 2>&1 || true
  [ -f "$REDIS_PID" ] && kill "$(cat "$REDIS_PID")" 2>/dev/null || true
  [ -f "$NATS_PID" ] && kill "$(cat "$NATS_PID")" 2>/dev/null || true
  echo "stopped"
}

status() {
  "$PG_BIN/pg_ctl" -D "$PGDATA" status 2>/dev/null || echo "postgres: down"
  if [ -f "$REDIS_PID" ] && kill -0 "$(cat "$REDIS_PID")" 2>/dev/null; then echo "redis: up"; else echo "redis: down"; fi
  if [ -f "$NATS_PID" ] && kill -0 "$(cat "$NATS_PID")" 2>/dev/null; then echo "nats: up"; else echo "nats: down"; fi
}

case "${1:-start}" in
  start) start_postgres; start_redis; start_nats ;;
  stop)  stop_all ;;
  status) status ;;
  *) echo "usage: $0 {start|stop|status}"; exit 1 ;;
esac
