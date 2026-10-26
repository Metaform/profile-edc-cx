#!/usr/bin/env bash
# Chapter 08 - the provider offers two assets, guarded by CEL-evaluated, credential-based
# access policies.
source "$(dirname "$0")/lib.sh"
require kubectl curl jq envsubst

PROVIDER_PCID="$(state_get provider-pcid)"
TOKEN="$(redline_token)"
P="${MGMT}/v5/participants/${PROVIDER_PCID}"

step "Policies"
for f in policy-contract-membership policy-access-processing policy-access-sensitive; do
  render "${PAYLOADS}/provider/${f}.json" | api POST "${P}/policydefinitions" "$TOKEN" - >/dev/null
  ok "$(jq -r '."@id"' "${PAYLOADS}/provider/${f}.json")"
done

step "Assets and contract definitions"
for lvl in processing sensitive; do
  render "${PAYLOADS}/provider/asset-${lvl}.json" | api POST "${P}/assets" "$TOKEN" - >/dev/null
  render "${PAYLOADS}/provider/contractdef-${lvl}.json" | api POST "${P}/contractdefinitions" "$TOKEN" - >/dev/null
  ok "aurora-docs-${lvl} (access requires level=${lvl})"
done
