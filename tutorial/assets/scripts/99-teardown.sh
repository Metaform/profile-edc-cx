#!/usr/bin/env bash
# Delete the KinD cluster and the local state.
source "$(dirname "$0")/lib.sh"
require kind
kind delete cluster -n "${KIND_CLUSTER}"
rm -rf "${STATE_DIR}"
ok "cluster '${KIND_CLUSTER}' and ${STATE_DIR} removed"
