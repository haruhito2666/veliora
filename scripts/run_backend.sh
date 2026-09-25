#!/bin/bash
# Start all Veliora backend services natively (no Docker).
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
GO_DIR="$ROOT/backend/go"
LOGS="$ROOT/.logs"
mkdir -p "$LOGS"

SERVICES=(org-manager constitution-engine dna-engine trust-engine memory-system knowledge-graph communication-engine caspian-bridge meeting-engine investigation-engine evolution-engine report-engine evidence-engine api-gateway)

pkill -f "veliora-backend/bin/" 2>/dev/null || true
mkdir -p "$GO_DIR/veliora-backend/bin"

echo "building services..."
(cd "$GO_DIR" && go build -o veliora-backend/bin/ ./cmd/...)

for svc in "${SERVICES[@]}"; do
  "$GO_DIR/veliora-backend/bin/$svc" > "$LOGS/$svc.log" 2>&1 &
  echo "started $svc (pid $!)"
done

sleep 2
echo "all services started. logs in $LOGS"
