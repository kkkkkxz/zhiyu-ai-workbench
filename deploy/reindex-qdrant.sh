#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1/api}"
USERNAME="${USERNAME:-admin}"
PASSWORD="${PASSWORD:-}"
TENANT_ID="${TENANT_ID:-}"
KB_ID="${KB_ID:-}"
LIMIT="${LIMIT:-}"
DRY_RUN="${DRY_RUN:-false}"

if [[ -z "$PASSWORD" ]]; then
  read -rsp "Password for ${USERNAME}: " PASSWORD
  echo
fi

login_json=$(curl -fsS -X POST "${BASE_URL}/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"${USERNAME}\",\"password\":\"${PASSWORD}\"}")

token=$(printf '%s' "$login_json" | sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
if [[ -z "$token" ]]; then
  echo "ERROR: login failed or accessToken not found" >&2
  echo "$login_json" >&2
  exit 1
fi

params=()
[[ -n "$TENANT_ID" ]] && params+=("tenantId=${TENANT_ID}")
[[ -n "$KB_ID" ]] && params+=("kbId=${KB_ID}")
[[ -n "$LIMIT" ]] && params+=("limit=${LIMIT}")
[[ "$DRY_RUN" == "true" ]] && params+=("dryRun=true")

query=""
if [[ "${#params[@]}" -gt 0 ]]; then
  query="?$(IFS='&'; echo "${params[*]}")"
fi

curl -fsS -X POST "${BASE_URL}/ops/rag/reindex-existing-chunks${query}" \
  -H "Authorization: Bearer ${token}"
