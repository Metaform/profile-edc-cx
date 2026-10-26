#!/usr/bin/env bash
# Chapter 09 - bootstrap the whole Aurora dataspace in one go.
#   ./run-all.sh --mode cfm      onboarding through the Connector Fabric Manager (default)
#   ./run-all.sh --mode manual   onboarding by hand, no orchestration
#   --skip-cluster               reuse an existing cluster with Traefik + Gateway API
set -euo pipefail
cd "$(dirname "$0")"

MODE=cfm
SKIP_CLUSTER=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode) MODE="$2"; shift 2 ;;
    --skip-cluster) SKIP_CLUSTER=true; shift ;;
    *) echo "unknown argument: $1" >&2; exit 1 ;;
  esac
done
[[ "$MODE" == cfm || "$MODE" == manual ]] || { echo "--mode must be cfm or manual" >&2; exit 1; }

$SKIP_CLUSTER || ./00-cluster.sh
./01-platform.sh
./02-issuer.sh
./03-connector-profile.sh
./04-onboard-"${MODE}".sh
./05-backend.sh
./06-provider-offer.sh
./07-consumer-transfer.sh
