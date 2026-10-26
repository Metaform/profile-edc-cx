#!/usr/bin/env bash
# Chapter 08 - deploy the provider's backend ("docshare") and put a document into it.
source "$(dirname "$0")/lib.sh"
require kubectl curl jq envsubst

step "Deploy docshare backend"
render "${TUTORIAL_ASSETS}/backend/docshare.yaml" | kubectl apply -f - >/dev/null
kubectl -n "${NS}" rollout status deployment/docshare --timeout 300s
ok "public API routed at ${BACKEND_PUBLIC_URL} (requires a Siglet token)"

step "Upload a sample document via the provider-internal control API"
port_forward docshare 18383 8083
existing="$(curl -sf -X POST "http://localhost:18383/api/control/certs/request" \
  -H 'Content-Type: application/json' -d '{}' | jq 'length')"
if [[ "${existing:-0}" -gt 0 ]]; then
  ok "backend already holds ${existing} document(s)"
else
  curl -sf -X POST "http://localhost:18383/api/control/certs" \
    -F 'metadata={"name":"aurora-quarterly-report","owner":"aurora-provider"}' \
    -F "file=@${TUTORIAL_ASSETS}/backend/samples/aurora-quarterly-report.txt;type=text/plain" | jq '{id, contentType}'
  ok "uploaded"
fi

step "Without a token the public API refuses access"
code="$(curl -s -o /dev/null -w '%{http_code}' -X POST "${BACKEND_PUBLIC_URL}/certs/request" \
  -H 'Content-Type: application/json' -d '{}')"
info "POST ${BACKEND_PUBLIC_URL}/certs/request (no token) -> HTTP ${code}"
