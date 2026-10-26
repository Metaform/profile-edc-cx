#!/usr/bin/env bash
# Chapter 07 - onboard the provider and the consumer WITHOUT CFM, doing by hand what each CFM
# agent does. Run on a fresh platform (or with different PROVIDER_SLUG/CONSUMER_SLUG) - not on
# top of 04-onboard-cfm.sh.
source "$(dirname "$0")/lib.sh"
require kubectl curl jq envsubst

# Platform-operator APIs that the gateway deliberately does not expose:
#   jwtlet management (:8081)  - who may obtain tokens for which participant context
#   siglet management (:8083)  - per-participant transfer-type mappings
port_forward jwtlet 18081 8081
port_forward siglet 18083 8083
JWTLET_MGMT="http://localhost:18081/api/v1"
SIGLET_MGMT="http://localhost:18083"

OPERATOR_TOKEN="$(operator_token)"

# workload_mapping <serviceaccount> <scopes-json-array>: let a platform workload obtain tokens
# for the current participant context (${PCID}). The template keeps "scopes": [] so it stays
# valid JSON; jq sets the array.
workload_mapping() {
  export WORKLOAD_SA="$1"
  render "${PAYLOADS}/manual/jwtlet-mapping-workload.json" \
    | jq --argjson scopes "$2" '.scopes = $scopes' \
    | api POST "${JWTLET_MGMT}/mappings" "$jwt_admin" - >/dev/null
}

# onboard <role> <participant-context-id> <did> <access-level>
onboard() {
  local role="$1"
  export PCID="$2" PARTICIPANT_DID="$3" ACCESS_LEVEL="$4" PARTICIPANT_NAME="Aurora ${1}"
  step "Onboard ${role}: context '${PCID}', DID ${PARTICIPANT_DID}, level=${ACCESS_LEVEL}"

  # 1. jwtlet (CFM jwtlet agent) ------------------------------------------------------------
  #    Bind the participant context to (a) the operator identity we use below and (b) the
  #    platform workloads that act on its behalf (vault access, DPS signaling).
  info "1/6 jwtlet: resource mappings"
  local jwt_admin
  jwt_admin="$(sa_token seed-jobs)"   # one of the two SAs allowed on the jwtlet management API
  render "${PAYLOADS}/manual/jwtlet-mapping-operator.json" | api POST "${JWTLET_MGMT}/mappings" "$jwt_admin" - >/dev/null
  workload_mapping controlplane '["read","signaling"]'   # vault + authorize DPS signaling
  workload_mapping identityhub  '["read"]'               # vault
  workload_mapping siglet-sa    '["read"]'               # vault

  # From here on we act with a token bound to this participant context.
  local pt
  pt="$(token redline "${PCID}" "management-api:admin identity-api:admin identity-api:credentials:read identity-api:credentials:write siglet-mgmt-api:read siglet-mgmt-api:write read write")"

  # 2. IdentityHub (CFM identityhub agent) ---------------------------------------------------
  #    Participant context + DID document + signing key + service endpoints.
  info "2/6 IdentityHub: participant context, DID document, key pair"
  render "${PAYLOADS}/manual/ih-participant.json" | api POST "${IDENTITY}/participants" "$pt" - >/dev/null
  wait_for "DID document at http://identity.${HOST}/${PARTICIPANT_DID##*:}/did.json" 30 2 \
    curl -sf "http://identity.${HOST}/${PARTICIPANT_DID##*:}/did.json"

  # 3. Control plane (CFM edcv agent) --------------------------------------------------------
  info "3/6 control plane: participant context, config, profile association"
  render "${PAYLOADS}/manual/cp-participant.json" | api POST "${MGMT}/v5/participants" "$pt" - >/dev/null
  render "${PAYLOADS}/manual/cp-config.json" | api PATCH "${MGMT}/v5/participants/${PCID}/config" "$pt" - >/dev/null
  render "${PAYLOADS}/manual/cp-profiles.json" | api PUT "${MGMT}/v5/participants/${PCID}/profiles" "$pt" - >/dev/null

  # 4. IssuerService (CFM registration agent) ------------------------------------------------
  #    Register the participant as a holder; its properties feed the holder attestation.
  info "4/6 IssuerService: holder"
  render "${PAYLOADS}/manual/issuer-holder.json" \
    | api POST "${ISSUER_ADMIN}/participants/${ISSUER_ID}/holders" "$OPERATOR_TOKEN" - >/dev/null

  # 5. DCP issuance (CFM onboarding agent) ---------------------------------------------------
  #    IdentityHub sends a DCP CredentialRequestMessage to the issuer and stores the result.
  info "5/6 DCP credential request"
  local headers location holder_pid status
  headers="$(mktemp)"
  render "${PAYLOADS}/manual/credential-request.json" \
    | curl -sf -D "$headers" -o /dev/null -X POST "${IDENTITY}/participants/${PCID}/credentials/request" \
        -H "Authorization: Bearer ${pt}" -H 'Content-Type: application/json' --data-binary @- \
    || die "credential request failed"
  location="$(grep -i '^location:' "$headers" | tr -d '\r' | awk '{print $2}')"
  rm -f "$headers"
  holder_pid="${location##*/}"
  [[ -n "$holder_pid" ]] || die "no Location header in credential request response"
  info "holderPid ${holder_pid}"
  for _ in $(seq 1 60); do
    status="$(api GET "${IDENTITY}/participants/${PCID}/credentials/request/${holder_pid}" "$pt" | jq -r '.status')"
    [[ "$status" == "ISSUED" ]] && break
    [[ "$status" == "ERROR" ]] && die "credential request ended in ERROR (check issuerservice logs)"
    sleep 5
  done
  [[ "$status" == "ISSUED" ]] || die "timed out waiting for credentials (last status: $status)"
  ok "credentials ISSUED"

  # 6. Siglet (CFM siglet agent) -------------------------------------------------------------
  #    Tell siglet where this participant's backend is, then register siglet as the
  #    participant's data plane in the control plane.
  info "6/6 Siglet: transfer-type mapping + data plane registration"
  local tt_body
  tt_body="$(render "${PAYLOADS}/manual/siglet-transfer-type-mapping.json")"
  if curl -sf -o /dev/null "${SIGLET_MGMT}/transfer-type-mappings/${PCID}" -H "Authorization: Bearer ${pt}"; then
    api PUT "${SIGLET_MGMT}/transfer-type-mappings/${PCID}" "$pt" - <<<"$tt_body" >/dev/null
  else
    api POST "${SIGLET_MGMT}/transfer-type-mappings" "$pt" - <<<"$tt_body" >/dev/null
  fi
  render "${PAYLOADS}/manual/cp-dataplane.json" | api PUT "${MGMT}/v5/participants/${PCID}/dataplanes" "$pt" - >/dev/null

  # Not replayed: the CFM key-management *lifecycle* agent is always running. It reacted to
  # IdentityHub's keypair-activated event (step 2) and published the key to siglet and the
  # control plane's STS config on its own.
  state_set "${role}-pcid" "${PCID}"
  ok "${role} onboarded"
}

onboard provider "${PROVIDER_SLUG}" "${PROVIDER_DID}" sensitive
onboard consumer "${CONSUMER_SLUG}" "${CONSUMER_DID}" processing

step "Credentials held by the consumer"
CT="$(token redline "$(state_get consumer-pcid)" "identity-api:credentials:read")"
api GET "${IDENTITY}/participants/$(state_get consumer-pcid)/credentials" "$CT" \
  | jq '[.[] | {type: .verifiableCredential.credential.type, subject: .verifiableCredential.credential.credentialSubject}]' \
  || warn "could not list credentials"
