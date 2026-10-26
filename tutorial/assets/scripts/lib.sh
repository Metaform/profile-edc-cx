#!/usr/bin/env bash
#
# Helper functions shared by the tutorial scripts. Source it, don't execute it.
#
# shellcheck source=env.sh
source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

# Strict mode only for scripts: sourcing this file into an interactive shell (chapter 09) must
# not make the first failing command close the terminal.
if [[ $- != *i* ]]; then set -euo pipefail; fi

# --- output ---------------------------------------------------------------------------------
step() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
info() { printf '    %s\n' "$*"; }
ok()   { printf '    \033[32m✔ %s\033[0m\n' "$*"; }
warn() { printf '    \033[33m! %s\033[0m\n' "$*" >&2; }
die()  { printf '\033[31m✘ %s\033[0m\n' "$*" >&2; if [[ $- == *i* ]]; then return 1; fi; exit 1; }

require() {
  for bin in "$@"; do
    command -v "$bin" >/dev/null 2>&1 || die "'$bin' is required but not installed"
  done
}

# --- state (values produced by one step and consumed by a later one) ------------------------
state_set() { printf '%s' "$2" > "${STATE_DIR}/$1"; }
state_get() {
  [[ -s "${STATE_DIR}/$1" ]] || die "state '$1' not found - did you run the previous steps?"
  cat "${STATE_DIR}/$1"
}

# --- payloads -------------------------------------------------------------------------------
# render <file>: substitute ${VAR} placeholders with exported environment variables.
render() { envsubst < "$1"; }

# --- tokens ---------------------------------------------------------------------------------
# sa_token <serviceaccount>: a short-lived Kubernetes ServiceAccount token, usable as the
# RFC 8693 subject token at jwtlet (and directly as bearer for the jwtlet management API).
sa_token() {
  kubectl -n "${NS}" create token "$1" --duration 1800s --audience "${K8S_AUDIENCE}"
}

# token <serviceaccount> <resource> <scopes>: exchange a ServiceAccount token for an EDC-V API
# token at jwtlet. <resource> is the participant context the token is bound to; <scopes> is a
# space-separated list of jwtlet scopes (role scopes like "admin" or direct ones like
# "management-api:admin").
token() {
  local sa="$1" resource="$2" scopes="$3" subject resp
  subject="$(sa_token "$sa")"
  resp="$(curl -s -X POST "${AUTH}" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'grant_type=urn:ietf:params:oauth:grant-type:token-exchange' \
    --data-urlencode "subject_token=${subject}" \
    --data-urlencode 'subject_token_type=urn:ietf:params:oauth:token-type:jwt' \
    --data-urlencode "resource=${resource}" \
    --data-urlencode "audience=${TOKEN_AUDIENCE}" \
    --data-urlencode "scope=${scopes}")"
  jq -er '.access_token' <<<"$resp" 2>/dev/null || die "token exchange failed for sa=$sa resource=$resource: $resp"
}

# Operator token used for platform-wide setup (issuer, profile, CFM). The seed-jobs
# ServiceAccount is bound to the "issuer" participant context by CPD, exactly like the
# platform's own seed jobs.
operator_token() { token seed-jobs "${ISSUER_ID}" "${1:-admin read write cfm-read cfm-write}"; }

# Token for the "redline" ServiceAccount (CPD's API facade identity). It carries the siglet
# token scopes the consumer needs to fetch its EDR.
redline_token() {
  token redline redline "admin read write cfm-read cfm-write siglet-token siglet-token:admin"
}

# --- HTTP -----------------------------------------------------------------------------------
# api <METHOD> <URL> <TOKEN> [<payload-file>|-]
# Prints the response body. 409 Conflict counts as success (idempotent re-runs); any other
# status >= 400 aborts the script.
api() {
  local method="$1" url="$2" tok="$3" body="${4:-}" out code
  out="$(mktemp)"
  local args=(-s -o "$out" -w '%{http_code}' -X "$method" "$url" -H "Authorization: Bearer ${tok}")
  if [[ -n "$body" ]]; then
    if [[ "$body" == "-" ]]; then body="/dev/stdin"; fi
    args+=(-H 'Content-Type: application/json' --data-binary "@${body}")
  fi
  code="$(curl "${args[@]}")" || true
  if [[ "$code" == "409" ]]; then
    warn "$method $url -> 409 (already exists, continuing)"
  elif [[ -z "$code" || "$code" -ge 400 || "$code" == "000" ]]; then
    cat "$out" >&2; echo >&2
    rm -f "$out"
    die "$method $url -> HTTP ${code:-none}"
  fi
  cat "$out"
  rm -f "$out"
}

# wait_for <description> <attempts> <sleep-seconds> <command...>
wait_for() {
  local what="$1" attempts="$2" pause="$3"; shift 3
  for ((i = 1; i <= attempts; i++)); do
    if "$@" >/dev/null 2>&1; then ok "$what"; return 0; fi
    sleep "$pause"
  done
  die "timed out waiting for: $what"
}

# --- port-forwards (APIs that the gateway deliberately does not expose) ---------------------
PF_PIDS=()
cleanup_port_forwards() { for p in "${PF_PIDS[@]:-}"; do [[ -n "$p" ]] && kill "$p" 2>/dev/null || true; done; }
if [[ $- != *i* ]]; then trap cleanup_port_forwards EXIT; fi

# port_forward <service> <local-port> <remote-port>
port_forward() {
  kubectl -n "${NS}" port-forward "svc/$1" "$2:$3" >/dev/null 2>&1 &
  PF_PIDS+=("$!")
  wait_for "port-forward svc/$1 -> localhost:$2" 30 1 bash -c "exec 3<>/dev/tcp/127.0.0.1/$2"
}
