#!/usr/bin/env bash
# =============================================================================
# seed_api.sh — Load all mock data into the running API
#
# Usage:
#   ./scripts/seed_api.sh <API_KEY>
#
# Example:
#   ./scripts/seed_api.sh a1b2c3d4-e5f6-7890-abcd-ef1234567890
# =============================================================================

set -euo pipefail

API_KEY="${1:-}"
BASE_URL="http://localhost:8080"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DATA_DIR="$SCRIPT_DIR/mock_data"

if [[ -z "$API_KEY" ]]; then
  echo "Usage: $0 <API_KEY>"
  echo "Get your API key from the app → Ajustes → API REST tab."
  exit 1
fi

header() { echo ""; echo "==> $*"; }
ok()     { echo "    OK: $*"; }
fail()   { echo "    ERROR: $*" >&2; }

check_server() {
  header "Checking server at $BASE_URL..."
  if ! curl -sf -H "X-API-Key: $API_KEY" "$BASE_URL/api/health" > /dev/null; then
    echo ""
    echo "  Cannot reach $BASE_URL/api/health"
    echo "  Make sure the app is running (mvn javafx:run) and the API key is correct."
    exit 1
  fi
  ok "Server is up"
}

import_resource() {
  local label="$1"
  local endpoint="$2"
  local file="$3"

  header "Importing $label from $(basename "$file")..."

  response=$(curl -sf \
    -X POST "$BASE_URL$endpoint" \
    -H "X-API-Key: $API_KEY" \
    -H "Content-Type: application/json" \
    --data-binary "@$file" 2>&1) || {
      fail "Request failed for $endpoint"
      return
    }

  imported=$(echo "$response" | grep -o '"imported":[0-9]*' | grep -o '[0-9]*' || echo "?")
  skipped=$(echo  "$response" | grep -o '"skipped":[0-9]*'  | grep -o '[0-9]*' || echo "?")
  ok "imported=$imported  skipped=$skipped"

  # Print errors if any
  errors=$(echo "$response" | python3 -c "
import sys, json
d = json.load(sys.stdin)
errs = d.get('errors', [])
for e in errs[:5]:
    print('   !', e)
if len(errs) > 5:
    print('   ... and', len(errs)-5, 'more errors')
" 2>/dev/null || true)
  [[ -n "$errors" ]] && echo "$errors"
}

# --------------------------------------------------------------------------
check_server
import_resource "Students"       "/api/students/import"         "$DATA_DIR/students.json"
import_resource "Staff"          "/api/staff/import"            "$DATA_DIR/staff.json"
import_resource "Visitor Badges" "/api/visitors/badges/import"  "$DATA_DIR/visitor_badges.json"

echo ""
echo "==> Seed complete. Run a quick export to verify:"
echo "    curl -s -H 'X-API-Key: $API_KEY' $BASE_URL/api/students/export | python3 -m json.tool | head -30"
echo ""
