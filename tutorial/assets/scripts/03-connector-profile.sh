#!/usr/bin/env bash
# Chapter 05 - teach the control plane the Aurora dialect: profile, trusted issuer, DCP scopes,
# CEL expressions.
source "$(dirname "$0")/lib.sh"
require kubectl curl jq envsubst

TOKEN="$(operator_token)"

step "Dataspace profile '${PROFILE}' (protocol + trusted issuer)"
render "${PAYLOADS}/connector/dataspaceprofile.json" > "${STATE_DIR}/dataspaceprofile.json"
code="$(curl -s -o /dev/null -w '%{http_code}' -X POST "${MGMT}/v5/dataspaceprofiles" \
  -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json' \
  --data-binary "@${STATE_DIR}/dataspaceprofile.json")"
if [[ "$code" == "409" ]]; then
  # POST is create-only; PUT updates an existing profile (upsert for re-runs)
  api PUT "${MGMT}/v5/dataspaceprofiles" "$TOKEN" "${STATE_DIR}/dataspaceprofile.json" >/dev/null
  ok "updated"
elif [[ "$code" -lt 300 ]]; then
  ok "created"
else
  die "POST /v5/dataspaceprofiles -> HTTP $code"
fi

step "DCP scopes (which credentials to request from a counterparty's credential service)"
for s in membership dataprocessor; do
  render "${PAYLOADS}/connector/dcpscope-${s}.json" | api POST "${MGMT}/v5/dcpscopes" "$TOKEN" - >/dev/null
  ok "$(jq -r '.value' "${PAYLOADS}/connector/dcpscope-${s}.json")"
done

step "CEL expressions (how to evaluate the Aurora policy left operands)"
for e in membership dataaccess-level; do
  render "${PAYLOADS}/connector/celexpression-${e}.json" | api POST "${MGMT}/v5/celexpressions" "$TOKEN" - >/dev/null
  ok "$(render "${PAYLOADS}/connector/celexpression-${e}.json" | jq -r '.leftOperand')"
done

step "Try the CEL test endpoint"
render "${PAYLOADS}/connector/celexpression-test.json" \
  | api POST "${MGMT}/v5/celexpressions/test" "$TOKEN" - | jq . \
  || warn "CEL test endpoint not available in this build - skipping"
