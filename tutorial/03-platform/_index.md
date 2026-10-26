---
title: "3. The platform"
description: Run the Core Platform Distribution on KinD, make did:web resolvable and obtain API tokens.
weight: 30
---

<!-- TOC -->
* [Cluster and gateway](#cluster-and-gateway)
* [Install the Core Platform Distribution](#install-the-core-platform-distribution)
* [did:web needs DNS inside the cluster](#didweb-needs-dns-inside-the-cluster)
* [The route table](#the-route-table)
* [Getting an API token](#getting-an-api-token)
* [Putting it together](#putting-it-together)
* [Related](#related)
<!-- TOC -->

Scripts: [`00-cluster.sh`](../assets/scripts/00-cluster.sh), [`01-platform.sh`](../assets/scripts/01-platform.sh).

## Cluster and gateway

CPD exposes all of its APIs through one Gateway API `Gateway`, `edcv-gateway`. Traefik implements it. On KinD, map
the host's ports 80 and 443 into the node so the gateway is reachable at `http://cpd.localhost`
([`kind-config.yaml`](../assets/kind/kind-config.yaml)):

```bash
kind create cluster -n aurora --config tutorial/assets/kind/kind-config.yaml
kubectl apply --server-side --force-conflicts \
  -f https://github.com/kubernetes-sigs/gateway-api/releases/download/v1.5.1/standard-install.yaml
helm repo add traefik https://traefik.github.io/charts
helm upgrade --install traefik traefik/traefik -n traefik --create-namespace \
  -f tutorial/assets/kind/traefik-values.yaml --wait
```

[`traefik-values.yaml`](../assets/kind/traefik-values.yaml) binds Traefik to the host ports and enables its
Gateway API provider. It disables Traefik's own GatewayClass and Gateway, because CPD creates them.

## Install the Core Platform Distribution

```bash
helm upgrade --install core-platform oci://ghcr.io/eclipse-cfm/charts/core-platform-distribution \
  --version 0.0.33 \
  -n edc-v --create-namespace \
  -f tutorial/assets/cpd/aurora-values.yaml \
  --wait --timeout 15m
```

> The tutorial is written against CPD **0.0.33**. The value names and routes it relies on can change between
> releases, so keep the pin. `01-platform.sh` reads it from `CPD_VERSION` in `env.sh`. If `helm` fails with
> `403 denied` on `ghcr.io`, run `helm registry logout ghcr.io` and retry.

[`aurora-values.yaml`](../assets/cpd/aurora-values.yaml) changes only what Aurora needs:

| Value | Why |
|-------|-----|
| `edc.controlplane.dataspaceProfiles.advertisedProfile: aurora-2025` | Each participant's DID document advertises its DSP endpoint as `…/api/dsp/<ctx>/<advertisedProfile>`. It **must** be the name of the profile we register in chapter 05, or counterparties that discover the endpoint through the DID document get a 404. |
| `edc.siglet.refreshEndpointPolicy.allowHttp: true` | Everything runs on plain HTTP locally. |
| `edc.siglet.tokenApiAuth.*` | Lets the consumer fetch its access token from Siglet's token API. |
| `edc.siglet.transferTypes` | Platform-wide fallback mapping for `HttpData-PULL`. Each participant overrides it during onboarding. |

While `--wait` runs, the post-install hooks seed the platform:
- **jwtlet:** scope mappings and the `seed-jobs`, `redline` and `issuerservice` ServiceAccount mappings.
- **IssuerService:** the `issuer` participant context, with DID `did:web:issuer.cpd.localhost:issuer`.
- **Tenant Manager:** one active cell.
- **Provision Manager:** the activity and orchestration definitions for onboarding.

## did:web needs DNS inside the cluster

Your machine resolves `issuer.cpd.localhost` to `127.0.0.1`, which reaches the gateway. **Inside a pod**,
`*.localhost` also resolves to loopback, which is the pod itself. When a control plane resolves
`did:web:identity.cpd.localhost:aurora-consumer`, the request must reach the gateway instead. CoreDNS therefore
rewrites the three hostnames to the Traefik service:

```
rewrite name cpd.localhost          traefik.traefik.svc.cluster.local
rewrite name identity.cpd.localhost traefik.traefik.svc.cluster.local
rewrite name issuer.cpd.localhost   traefik.traefik.svc.cluster.local
```

`01-platform.sh` adds these lines to the CoreDNS `Corefile` between marker comments, restarts CoreDNS and verifies
the result:

```bash
kubectl -n edc-v run dnscheck --rm -it --restart=Never --image=debian:stable-slim -- getent hosts issuer.cpd.localhost
curl -s http://issuer.cpd.localhost/issuer/did.json | jq .id
```

> Use a glibc-based image (`debian`), not `curl` or Alpine images, for the check. Both short-circuit `*.localhost`
> to loopback without asking DNS.

On a real cluster with real DNS names you skip this step. You set `global.host` (and optionally
`edc.issuerservice.did.*` / `edc.identityhub.did.host`) to resolvable names instead.

## The route table

Every chapter uses these gateway routes. Most of them are protected by the `jwt-auth` middleware, which forwards to
clearglass for scope checks.

| Public prefix (on `cpd.localhost`) | Backend | Auth | Used in |
|------------------------------------|---------|------|---------|
| `/api/auth/token` | jwtlet token exchange | – | everywhere |
| `/api/management` → `/api/mgmt` | control plane Management API | ✔ | 05, 07, 08 |
| `/api/dsp` | control plane DSP | – (DCP) | 08 |
| `/api/identity` → `/api/identity/v1` | IdentityHub Identity API | ✔ | 07 |
| `/api/credentials` | IdentityHub DCP credential service | – (DCP) | – |
| `/api/issuer/admin` → `/api/admin/v1` | IssuerService Admin API | ✔ | 04, 07 |
| `/api/issuance` | IssuerService DCP issuance | – (DCP) | – |
| `/api/tm` → `/api/v1alpha1` | CFM Tenant Manager | ✔ | 06 |
| `/api/pm` → `/api/v1alpha1` | CFM Provision Manager | ✔ | 06 |
| `/api/siglet` | Siglet token API | ✔ | 08 |
| `/api/siglet/keys` | Siglet JWKS | – | 08 |
| host `identity.cpd.localhost` | IdentityHub DID documents | – | 07 |
| host `issuer.cpd.localhost` | IssuerService DID document | – | 03 |

Two platform-operator APIs are deliberately **not** routed: the jwtlet management API (`:8081`) and the Siglet
management API (`:8083`). Only CFM's agents call them. Chapter 07 reaches them with `kubectl port-forward`.

## Getting an API token

The platform has no static API keys. A caller presents a **Kubernetes ServiceAccount token** to jwtlet and gets an
access token bound to one participant context (RFC 8693 token exchange):

```bash
SUBJECT=$(kubectl -n edc-v create token seed-jobs --duration 1800s \
            --audience https://kubernetes.default.svc.cluster.local)
curl -s -X POST http://cpd.localhost/api/auth/token \
  --data-urlencode grant_type=urn:ietf:params:oauth:grant-type:token-exchange \
  --data-urlencode subject_token="$SUBJECT" \
  --data-urlencode subject_token_type=urn:ietf:params:oauth:token-type:jwt \
  --data-urlencode resource=issuer \
  --data-urlencode audience=edcv \
  --data-urlencode scope="admin read write cfm-read cfm-write" | jq -r .access_token
```

- `resource` is the participant context the token acts for.
- jwtlet issues a token only if a **mapping** exists for that ServiceAccount, that participant context and those
  scopes.

CPD seeds two mappings that this tutorial uses:

| ServiceAccount | Participant context | Scopes | Used for |
|----------------|---------------------|--------|----------|
| `seed-jobs` | `issuer` | `read write admin cfm-read cfm-write` | Dataspace operator: issuer setup, profile, CFM (`operator_token` in [`lib.sh`](../assets/scripts/lib.sh)) |
| `redline` | `redline` | `… admin … siglet-token siglet-token:admin` | Acting on participants' Management APIs and the Siglet token API (`redline_token`) |

Some scopes are *role scopes* that jwtlet expands into API scopes:
- `admin` becomes `management-api:admin identity-api:admin issuer-admin-api:admin`.
- `cfm-write` becomes `tenant-manager-api:write provision-manager-api:write`.

Clearglass treats `admin ⊇ write ⊇ read`, and an API-level scope such as `identity-api:admin` covers resource-level
requirements such as `identity-api:participants:write`.

The helpers `token <sa> <resource> <scopes>`, `operator_token` and `redline_token` in
[`lib.sh`](../assets/scripts/lib.sh) wrap this exchange.

## Putting it together

- [ ] KinD cluster with ports 80/443 mapped; Gateway API CRDs; Traefik
- [ ] CPD installed with `advertisedProfile: aurora-2025`
- [ ] CoreDNS rewrites for `cpd.localhost`, `identity.cpd.localhost`, `issuer.cpd.localhost`
- [ ] `curl http://issuer.cpd.localhost/issuer/did.json` returns the issuer DID document
- [ ] You can mint an operator token

## Related

- [Core Platform Distribution README](https://github.com/eclipse-cfm/core-platform-distribution)
- [JAD, the reference deployment this setup is derived from](https://github.com/eclipse-dataspace-hub/jad)
