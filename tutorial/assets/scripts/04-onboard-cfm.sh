#!/usr/bin/env bash
# Chapter 06 - onboard the provider and the consumer through the Connector Fabric Manager.
source "$(dirname "$0")/lib.sh"
require kubectl curl jq envsubst

TOKEN="$(operator_token "cfm-read cfm-write")"

step "Pick the cell (deployment zone) CPD seeded"
CELL_ID="$(api GET "${TM}/cells" "$TOKEN" | jq -er 'map(select(.state == "active")) | first | .id')"
export CELL_ID
ok "cell ${CELL_ID}"

step "CFM dataspace profile '${PROFILE}' (which credentials a participant must obtain)"
DATASPACE_ID="$(api GET "${TM}/dataspace-profiles" "$TOKEN" \
  | jq -r --arg n "${PROFILE}" 'map(select(.properties.name == $n)) | first | .id // empty')"
if [[ -z "${DATASPACE_ID}" ]]; then
  DATASPACE_ID="$(render "${PAYLOADS}/cfm/dataspace-profile.json" \
    | api POST "${TM}/dataspace-profiles" "$TOKEN" - | jq -er '.id')"
  export DATASPACE_ID
  # A participant can only be placed in a cell that has an active deployment of its profile.
  render "${PAYLOADS}/cfm/profile-deployment.json" \
    | api POST "${TM}/dataspace-profiles/${DATASPACE_ID}/deployments" "$TOKEN" - >/dev/null
  ok "created ${DATASPACE_ID} and deployed it to cell ${CELL_ID}"
else
  ok "exists: ${DATASPACE_ID}"
fi
export DATASPACE_ID
state_set dataspace-id "${DATASPACE_ID}"

# onboard <role> <did> <access-level>
onboard() {
  local role="$1"
  export PARTICIPANT_DID="$2" ACCESS_LEVEL="$3" TENANT_NAME="Aurora ${1}"
  step "Onboard ${role} (${PARTICIPANT_DID}, level=${ACCESS_LEVEL})"
  if [[ -s "${STATE_DIR}/${role}-pcid" ]]; then
    ok "already onboarded: participant context $(state_get "${role}-pcid")"
    return
  fi

  local tenant profile
  tenant="$(render "${PAYLOADS}/cfm/tenant.json" | api POST "${TM}/tenants" "$TOKEN" - | jq -er '.id')"
  info "tenant ${tenant}"
  profile="$(render "${PAYLOADS}/cfm/participant-profile.json" \
    | api POST "${TM}/tenants/${tenant}/participant-profiles" "$TOKEN" - | jq -er '.id')"
  info "participant profile ${profile} - CFM orchestration started"

  # The deploy orchestration runs asynchronously (jwtlet -> identityhub/edcv/registration ->
  # siglet -> onboarding). Done when every VPA is active; the onboarding agent then publishes
  # the participant context id and the credential request in cfm.vpa.state.
  local url="${TM}/tenants/${tenant}/participant-profiles/${profile}" body
  for _ in $(seq 1 90); do
    body="$(api GET "$url" "$TOKEN")"
    if jq -e '.error == true' <<<"$body" >/dev/null; then
      die "onboarding failed: $(jq -r '.errorDetail' <<<"$body")"
    fi
    if jq -e '(.vpas | length > 0) and all(.vpas[]; .state == "active")
              and (.properties["cfm.vpa.state"].participantContextId != null)' <<<"$body" >/dev/null; then
      break
    fi
    info "waiting... $(jq -c '[.vpas[]? | {(.type): .state}] | add' <<<"$body")"
    sleep 5
  done
  jq -e '.properties["cfm.vpa.state"].participantContextId' <<<"$body" >/dev/null \
    || die "timed out waiting for ${role} onboarding"

  jq '{identifier, vpas: [.vpas[] | {type, state}], state: .properties["cfm.vpa.state"]}' <<<"$body"
  state_set "${role}-pcid" "$(jq -r '.properties["cfm.vpa.state"].participantContextId' <<<"$body")"
  state_set "${role}-tenant" "${tenant}"
  state_set "${role}-profile" "${profile}"
  ok "${role} participant context: $(state_get "${role}-pcid")"
}

onboard provider "${PROVIDER_DID}" sensitive
onboard consumer "${CONSUMER_DID}" processing
