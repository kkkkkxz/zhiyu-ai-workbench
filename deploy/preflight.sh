#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

ENV_FILE="${1:-.env}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.deploy.yml}"
NGINX_CONF="${NGINX_CONF:-nginx/app.conf}"

fail=0

err() {
  echo "ERROR: $*" >&2
  fail=1
}

warn() {
  echo "WARN: $*" >&2
}

info() {
  echo "OK: $*"
}

if [[ ! -f "$ENV_FILE" ]]; then
  err "$ENV_FILE not found. Run ./deploy.sh once to generate it."
else
  # shellcheck disable=SC1090
  set -a; source "$ENV_FILE"; set +a
  info "loaded $ENV_FILE"
fi

required=(
  DASHSCOPE_API_KEY JWT_SECRET MYSQL_PASSWORD MYSQL_ROOT_PASSWORD
  REDIS_PASSWORD RABBIT_PASSWORD QDRANT_API_KEY MINIO_ACCESS_KEY
  MINIO_SECRET_KEY
)

for key in "${required[@]}"; do
  value="${!key:-}"
  if [[ -z "$value" ]]; then
    err "$key is empty"
  fi
done

weak_values=(
  "123456" "root" "root123" "password" "admin" "guest"
  "zwsk_dev_pwd" "zwsk_root_pwd" "zwsk_redis_pwd"
  "minioadmin" "minio_dev_pwd_change_me"
)

for key in "${required[@]}"; do
  value="${!key:-}"
  for weak in "${weak_values[@]}"; do
    if [[ "$value" == "$weak" ]]; then
      err "$key uses weak/default value"
    fi
  done
done

if [[ "${#JWT_SECRET:-0}" -lt 64 ]]; then
  err "JWT_SECRET must be at least 64 characters"
fi

if [[ "${DASHSCOPE_API_KEY:-}" != sk-* ]]; then
  warn "DASHSCOPE_API_KEY does not look like a DashScope key"
fi

if [[ ! -f "$COMPOSE_FILE" ]]; then
  err "$COMPOSE_FILE not found"
else
  if grep -Eq '3306:3306|6379:6379|5672:5672|6333:6333|9000:9000|9001:9001|15672:15672' "$COMPOSE_FILE"; then
    err "$COMPOSE_FILE appears to publish internal middleware ports"
  else
    info "middleware ports are not published by compose"
  fi
fi

if [[ -f "$NGINX_CONF" ]]; then
  public_domain="${PUBLIC_DOMAIN:-}"
  if [[ -n "$public_domain" ]]; then
    if grep -Eq "server_name[[:space:]]+([^;[:space:]]+[[:space:]]+)*${public_domain//./\\.}([[:space:];]|$)" "$NGINX_CONF"; then
      info "nginx server_name includes PUBLIC_DOMAIN=$public_domain"
    else
      err "PUBLIC_DOMAIN is set to $public_domain, but nginx server_name is not configured for it"
    fi
    if [[ -z "${CORS_ALLOWED_ORIGINS:-}" ]]; then
      warn "CORS_ALLOWED_ORIGINS is empty; this is OK only when frontend and /api are served from the same domain"
    fi
  else
    warn "PUBLIC_DOMAIN is empty; set it before binding a real domain"
  fi
  if grep -Eq '^[[:space:]]*listen[[:space:]]+443[[:space:]]+ssl' "$NGINX_CONF"; then
    info "nginx HTTPS server block is enabled"
    if [[ -f "certs/fullchain.pem" && -f "certs/privkey.pem" ]]; then
      info "nginx TLS certificates exist in deploy/certs"
    else
      err "nginx HTTPS is enabled, but certs/fullchain.pem or certs/privkey.pem is missing"
    fi
  else
    warn "nginx HTTPS block is not enabled; public production must terminate HTTPS at Nginx, SLB, CDN, or WAF"
  fi
else
  err "$NGINX_CONF not found"
fi

if [[ -f "../src/main/resources/application-prod.yaml" ]]; then
  if grep -Eq 'sk-[A-Za-z0-9_.-]{20,}|password: [^$({]' "../src/main/resources/application-prod.yaml"; then
    err "application-prod.yaml may contain hard-coded secrets"
  else
    info "application-prod.yaml has no obvious hard-coded secret"
  fi
fi

if [[ "$fail" -ne 0 ]]; then
  echo "Preflight failed. Fix the errors above before production deploy." >&2
  exit 1
fi

echo "Preflight passed."
