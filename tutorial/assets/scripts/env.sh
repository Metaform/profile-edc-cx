#!/usr/bin/env bash
#
# Shared settings for the "Aurora" tutorial scripts. Every value can be overridden from the
# environment, e.g. `HOST=example.localhost ./run-all.sh`.
#

# --- platform -------------------------------------------------------------------------------
export NS="${NS:-edc-v}"                       # namespace CPD is installed into
export RELEASE="${RELEASE:-core-platform}"     # CPD Helm release name
export CPD_CHART="${CPD_CHART:-oci://ghcr.io/eclipse-cfm/charts/core-platform-distribution}"
export CPD_VERSION="${CPD_VERSION:-0.0.33}"    # CPD chart release the tutorial is written against
export KIND_CLUSTER="${KIND_CLUSTER:-aurora}"
export HOST="${HOST:-cpd.localhost}"           # CPD global.host
export BASE="${BASE:-http://${HOST}}"          # gateway base URL as seen from your machine
export K8S_AUDIENCE="${K8S_AUDIENCE:-https://kubernetes.default.svc.cluster.local}"
export TOKEN_AUDIENCE="${TOKEN_AUDIENCE:-edcv}"

# Gateway routes (see the CPD route table in chapter 03)
export MGMT="${BASE}/api/management"           # -> controlplane /api/mgmt
export IDENTITY="${BASE}/api/identity"         # -> identityhub /api/identity/v1
export ISSUER_ADMIN="${BASE}/api/issuer/admin" # -> issuerservice /api/admin/v1
export TM="${BASE}/api/tm"                     # -> tenant-manager /api/v1alpha1
export SIGLET="${BASE}/api/siglet"             # -> siglet :8080
export AUTH="${BASE}/api/auth/token"           # -> jwtlet /token

# In-cluster addresses (used in payloads that other services dereference)
export SVC="${NS}.svc.cluster.local"
export DSP_INTERNAL="http://controlplane.${SVC}:8082/api/dsp"
export VAULT_URL="http://${RELEASE}-vault.${SVC}:8200"

# --- dataspace ------------------------------------------------------------------------------
export PROFILE="${PROFILE:-aurora-2025}"                         # EDC DataspaceProfile name
export AURORA_VOCAB="${AURORA_VOCAB:-https://w3id.org/aurora/policy/}"
export ISSUER_ID="issuer"                                        # issuer participant context (seeded by CPD)
export ISSUER_DID="${ISSUER_DID:-did:web:issuer.${HOST}:issuer}"
export DID_BASE="did:web:identity.${HOST}"
export PROVIDER_SLUG="${PROVIDER_SLUG:-aurora-provider}"
export CONSUMER_SLUG="${CONSUMER_SLUG:-aurora-consumer}"
export PROVIDER_DID="${DID_BASE}:${PROVIDER_SLUG}"
export CONSUMER_DID="${DID_BASE}:${CONSUMER_SLUG}"

# --- sample backend -------------------------------------------------------------------------
export BACKEND_PUBLIC_URL="${BACKEND_PUBLIC_URL:-${BASE}/api/docshare}"

# --- layout ---------------------------------------------------------------------------------
TUTORIAL_ASSETS="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export TUTORIAL_ASSETS
export PAYLOADS="${TUTORIAL_ASSETS}/payloads"
export STATE_DIR="${STATE_DIR:-${TUTORIAL_ASSETS}/.state}"   # participant context ids etc.
mkdir -p "${STATE_DIR}"
