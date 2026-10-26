#!/usr/bin/env bash
# Chapter 04 - prepare the IssuerService: attestation definition + credential definitions.
source "$(dirname "$0")/lib.sh"
require kubectl curl jq envsubst

TOKEN="$(operator_token)"
BASE_PATH="${ISSUER_ADMIN}/participants/${ISSUER_ID}"

step "Attestation definition (holder attestation: claims come from the holder's properties)"
render "${PAYLOADS}/issuer/attestation-holder.json" | api POST "${BASE_PATH}/attestations" "$TOKEN" - >/dev/null
ok "aurora-holder-attestation"

step "Credential definitions"
for def in membership dataprocessor; do
  render "${PAYLOADS}/issuer/credentialdef-${def}.json" \
    | api POST "${BASE_PATH}/credentialdefinitions" "$TOKEN" - >/dev/null
  ok "$(jq -r '.id + " (" + .credentialType + ")"' "${PAYLOADS}/issuer/credentialdef-${def}.json")"
done

step "Read back"
api GET "${BASE_PATH}/credentialdefinitions/aurora-membership-def" "$TOKEN" \
  | jq '{id, credentialType, attestations, mappings}' \
  || warn "could not read back the credential definition (the definitions were created above)"
