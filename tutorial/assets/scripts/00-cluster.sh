#!/usr/bin/env bash
# Chapter 03 - create a KinD cluster with Traefik as Gateway API controller.
source "$(dirname "$0")/lib.sh"
require kind kubectl helm

step "Create KinD cluster '${KIND_CLUSTER}' (host ports 80/443 -> gateway)"
if kind get clusters | grep -qx "${KIND_CLUSTER}"; then
  ok "cluster already exists"
else
  kind create cluster -n "${KIND_CLUSTER}" --config "${TUTORIAL_ASSETS}/kind/kind-config.yaml"
fi
kubectl config use-context "kind-${KIND_CLUSTER}" >/dev/null

step "Install Gateway API CRDs"
kubectl apply --server-side --force-conflicts \
  -f https://github.com/kubernetes-sigs/gateway-api/releases/download/v1.5.1/standard-install.yaml >/dev/null
ok "Gateway API v1.5.1"

step "Install Traefik (Gateway API controller)"
helm repo add traefik https://traefik.github.io/charts >/dev/null 2>&1 || true
helm repo update traefik >/dev/null
helm upgrade --install traefik traefik/traefik -n traefik --create-namespace \
  -f "${TUTORIAL_ASSETS}/kind/traefik-values.yaml" --wait >/dev/null
ok "Traefik running in namespace 'traefik'"
