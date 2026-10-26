#!/usr/bin/env bash
# Chapter 03 - install the Core Platform Distribution and make did:web resolvable in-cluster.
source "$(dirname "$0")/lib.sh"
require kubectl helm curl jq

step "Install Core Platform Distribution (release '${RELEASE}', namespace '${NS}')"
version_args=()
[[ -n "${CPD_VERSION}" ]] && version_args=(--version "${CPD_VERSION}")
helm upgrade --install "${RELEASE}" "${CPD_CHART}" "${version_args[@]}" \
  -n "${NS}" --create-namespace \
  -f "${TUTORIAL_ASSETS}/cpd/aurora-values.yaml" \
  --wait --timeout 15m
ok "CPD installed (post-install seed hooks completed)"

step "CoreDNS: resolve *.${HOST} to the gateway from inside the cluster"
# *.localhost resolves to loopback inside pods. did:web resolution (issuer.<host>,
# identity.<host>) must reach the gateway instead, so CoreDNS rewrites those names to the
# Traefik service.
TARGET="traefik.traefik.svc.cluster.local"
BEGIN="# BEGIN aurora did-dns"
END="# END aurora did-dns"
tmp="$(mktemp -d)"
kubectl -n kube-system get configmap coredns -o jsonpath='{.data.Corefile}' > "$tmp/Corefile.cur"
{
  echo "    ${BEGIN}"
  for h in "${HOST}" "identity.${HOST}" "issuer.${HOST}"; do echo "    rewrite name ${h} ${TARGET}"; done
  echo "    ${END}"
} > "$tmp/block"
awk -v b="${BEGIN}" -v e="${END}" '{t=$0; sub(/^[ \t]+/,"",t)} t==b{skip=1} !skip{print} t==e{skip=0}' "$tmp/Corefile.cur" \
  | sed "/^\.:53 {/r $tmp/block" > "$tmp/Corefile.new"
if cmp -s "$tmp/Corefile.cur" "$tmp/Corefile.new"; then
  ok "rewrites already present"
else
  kubectl -n kube-system create configmap coredns --from-file=Corefile="$tmp/Corefile.new" \
    --dry-run=client -o yaml | kubectl apply -f - >/dev/null
  kubectl -n kube-system rollout restart deployment/coredns >/dev/null
  kubectl -n kube-system rollout status deployment/coredns --timeout 120s >/dev/null
  ok "rewrites applied"
fi
rm -rf "$tmp"

step "Verify"
# glibc getent (not curl/alpine: both short-circuit *.localhost to loopback)
dns_check() {
  kubectl -n "${NS}" run "dnscheck-${RANDOM}" --rm -i --restart=Never --image=debian:stable-slim \
    -- getent hosts "issuer.${HOST}"
}
wait_for "issuer.${HOST} resolves in-cluster" 30 2 dns_check
wait_for "issuer DID document served at http://issuer.${HOST}/issuer/did.json" 30 2 \
  curl -sf "http://issuer.${HOST}/issuer/did.json"
curl -s "http://issuer.${HOST}/issuer/did.json" | jq '{id, service: [.service[]?.type]}'
