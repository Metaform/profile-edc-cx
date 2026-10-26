#!/usr/bin/env bash
# Chapter 08 - the consumer browses the catalog, negotiates, transfers and calls the backend.
source "$(dirname "$0")/lib.sh"
require kubectl curl jq envsubst

PROVIDER_PCID="$(state_get provider-pcid)"
CONSUMER_PCID="$(state_get consumer-pcid)"
export PROVIDER_PCID CONSUMER_PCID
TOKEN="$(redline_token)"
C="${MGMT}/v5/participants/${CONSUMER_PCID}"
DATASET="aurora-docs-processing"

step "Catalog request (DSP + DCP: the provider evaluates our credentials with CEL)"
catalog="$(render "${PAYLOADS}/consumer/catalog-request.json" | api POST "${C}/catalog/request" "$TOKEN" -)"
datasets="$(jq -c '[.dataset] | flatten | map(."@id")' <<<"$catalog")"
info "datasets visible to the consumer: ${datasets}"
jq -e --arg d "$DATASET" 'index($d) != null' <<<"$datasets" >/dev/null \
  || die "expected ${DATASET} in the catalog"
ok "${DATASET} offered (consumer holds DataProcessorCredential level=processing)"
if jq -e 'index("aurora-docs-sensitive") != null' <<<"$datasets" >/dev/null; then
  die "aurora-docs-sensitive must NOT be visible to a level=processing consumer"
fi
ok "aurora-docs-sensitive hidden (its access policy requires level=sensitive)"

step "Contract negotiation"
offer="$(jq -c --arg d "$DATASET" '[.dataset] | flatten | map(select(."@id" == $d)) | first
  | [.hasPolicy] | flatten | first' <<<"$catalog")"
request="$(jq -n --argjson offer "$offer" --arg d "$DATASET" --arg profile "$PROFILE" \
  --arg addr "${DSP_INTERNAL}/${PROVIDER_PCID}/${PROFILE}" --arg assigner "$PROVIDER_DID" '{
    "@context": ["https://w3id.org/edc/connector/management/v2"],
    "@type": "ContractRequest",
    "protocol": $profile,
    "counterPartyAddress": $addr,
    "policy": ($offer + {"@type": "Offer", "target": $d, "assigner": $assigner})
  }')"
negotiation="$(api POST "${C}/contractnegotiations" "$TOKEN" - <<<"$request" | jq -er '."@id"')"
info "negotiation ${negotiation}"
for _ in $(seq 1 60); do
  n="$(api GET "${C}/contractnegotiations/${negotiation}" "$TOKEN")"
  state="$(jq -r '.state' <<<"$n")"
  [[ "$state" == "FINALIZED" ]] && break
  [[ "$state" == "TERMINATED" ]] && die "negotiation terminated: $(jq -r '.errorDetail // empty' <<<"$n")"
  sleep 2
done
[[ "$state" == "FINALIZED" ]] || die "negotiation did not finalize (state: $state)"
export AGREEMENT_ID="$(jq -r '.contractAgreementId' <<<"$n")"
ok "agreement ${AGREEMENT_ID}"

step "Transfer (HttpData-PULL via Siglet)"
transfer="$(render "${PAYLOADS}/consumer/transfer-request.json" \
  | api POST "${C}/transferprocesses" "$TOKEN" - | jq -er '."@id"')"
info "transfer ${transfer}"
for _ in $(seq 1 60); do
  t="$(api GET "${C}/transferprocesses/${transfer}" "$TOKEN")"
  state="$(jq -r '.state' <<<"$t")"
  [[ "$state" == "STARTED" ]] && break
  [[ "$state" == "TERMINATED" ]] && die "transfer terminated: $(jq -r '.errorDetail // empty' <<<"$t")"
  sleep 2
done
[[ "$state" == "STARTED" ]] || die "transfer did not start (state: $state)"
ok "transfer STARTED"

step "Access token (EDR) from the consumer's Siglet"
edr="$(api GET "${SIGLET}/tokens/${CONSUMER_PCID}/${transfer}" "$TOKEN")"
ACCESS_TOKEN="$(jq -er '.token' <<<"$edr")"
ENDPOINT="$(jq -r '.endpoint // empty' <<<"$edr")"
ENDPOINT="${ENDPOINT:-${BACKEND_PUBLIC_URL}}"
info "endpoint ${ENDPOINT}"
info "token claims: $(cut -d. -f2 <<<"$ACCESS_TOKEN" | tr '_-' '/+' | base64 -d 2>/dev/null | jq -c . 2>/dev/null || echo '(not decodable)')"

step "Call the provider's backend with the token"
docs="$(curl -sf -X POST "${ENDPOINT}/certs/request" -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H 'Content-Type: application/json' -d '{}')" || die "backend rejected the token"
jq . <<<"$docs"
doc_id="$(jq -er 'first | .id' <<<"$docs")"
curl -sf "${ENDPOINT}/certs/${doc_id}" -H "Authorization: Bearer ${ACCESS_TOKEN}"
echo
ok "data received - the dataspace works end to end"
