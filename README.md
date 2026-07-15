# Catena-X Profile for EDC

> **Status — DRAFT.** This targets the upcoming Catena-X *Neptune* release. The profile id
> (`cx-neptune`) and the JSON-LD context URLs are placeholders (`TBD`) to be confirmed with
> governance. See [`neptune.md`](./neptune.md) for the authoritative specification.
> It works also with the previous Catena-X release using the dsp protocol 2025-1.

This repository describes how to configure an [Eclipse EDC](https://github.com/eclipse-edc/Connector)
connector to participate in the **Catena-X** dataspace, together with the deployment artifacts and an
API collection to try it out end to end.

A *dataspace profile* is the bundle of settings an EDC runtime needs to speak a particular dataspace's
dialect. The Catena-X profile packages:

- the **Dataspace Protocol** wire version (`2025-1`, over HTTPS);
- the **Catena-X ODRL vocabulary** (from the [cx-odrl-profile](https://github.com/catenax-eV/cx-odrl-profile));
- the **DCP credential scopes** used to request the Verifiable Credentials Catena-X relies on
  (Membership, Bpn, DataExchangeGovernance);
- **CEL expressions** that evaluate the Catena-X ODRL operands at policy-evaluation time;
- **JSON Schema validators** that reject malformed Catena-X policies at the management API boundary.

The profile is designed for EDC **virtual mode** (multi-profile, multi-participant runtimes): a single
EDC runtime can serve the Catena-X profile alongside other dataspaces. The mechanics of each of the
pieces above — protocol namespaces, scope grammar, CEL semantics, schema wiring — are documented in
[`neptune.md`](./neptune.md); this README stays at the "what it is and how to run it" level and points
there for the details.

## Repository layout

| Path | What it is |
|------|------------|
| [`neptune.md`](./neptune.md) | The full profile specification — protocol, DCP scopes, CEL expressions, JSON Schema validation, JSON-LD contexts. **Start here for technical detail.** |
| [`charts/cx-profile/`](./charts/cx-profile) | Helm chart (`jad-catenax-profile`) that seeds the Catena-X profile onto a running platform. |
| [`charts/tractusx/`](./charts/tractusx) | Optional wrapper chart deploying a [Tractus-X](https://github.com/eclipse-tractusx/tractusx-edc) EDC connector + an in-memory BPN-DID Resolution Service. |
| [`cx-tck/`](./cx-tck) | Catena-X **Technology Compatibility Kit** — verifies a connector correctly implements the DSP exchange + DCP identity combination. See [`cx-tck/README.md`](./cx-tck/README.md). |
| [`requests/EDC-V-Onboarding-Cx/`](./requests/EDC-V-Onboarding-Cx) | [Bruno](https://usebruno.com) API collection that drives participant onboarding and a data-transfer demo. |
| [`scripts/token.sh`](./scripts/token.sh) | Mints a Management-API bearer token via a Kubernetes ServiceAccount token + RFC 8693 exchange. |

## Trying it out — the cx-profile

The chart layers **dataspace-specific seeding** on top of an already-running platform; it does not
stand up a connector by itself. The steps below deploy the Catena-X profile and then exercise it with
the Bruno collection.

### Prerequisites

You need a Kubernetes cluster (KinD is fine for local development) with a **Core Platform Distribution
(CPD)** deployment already running in the `edc-v` namespace. CPD provides everything the profile
seeds onto: the EDC `controlplane`, `identityhub`, `issuerservice`, `tenant-manager` and `siglet`
dataplane, the `jwtlet` token-exchange IDP, the `edcv-gateway` gateway (ingress on `jad.localhost`),
the platform `issuer` participant context, a provisioned cell, and the `seed-jobs` ServiceAccount.


For the cluster setup, see JAD [docs](https://github.com/eclipse-dataspace-hub/jad/).

Tooling: `helm`, `kubectl`, `kind`, `jq`, and the [Bruno](https://usebruno.com) desktop app.

### 1. Deploy the Core Platform Distribution

First, deploy the CPD platform itself:

```bash
helm upgrade --install core-platform oci://ghcr.io/eclipse-cfm/charts/core-platform-distribution \
    --version 0.0.6 \
    --namespace edc-v --create-namespace \
    --wait --timeout 15m
```

### 2. Deploy the profile

```bash
helm upgrade --install cx-profile charts/cx-profile/ \
              --namespace edc-v \
              --wait --timeout 10m
```

The chart contains no long-running workloads — only idempotent Helm post-install/upgrade hook Jobs
(they tolerate `409 Conflict`, so re-running is safe). They seed, in order:

1. **Issuer credential definitions** — `MembershipCredential`, `BpnCredential` and
   `DataExchangeGovernanceCredential` on the issuer service.
2. **Dataspace Profile** — registered in the tenant-manager and deployed onto the platform cell.
3. **The `cx-neptune` connector profile** — posted to the controlplane `/v5beta` Management API:
   the DataspaceProfile, cached JSON-LD contexts, the DCP scopes, and the three CEL expressions
   (Membership / FrameworkAgreement / BusinessPartnerNumber).

Each phase can be toggled independently via `seedJobs.*.enabled` in
[`charts/cx-profile/values.yaml`](./charts/cx-profile/values.yaml). See [`neptune.md`](./neptune.md)
§3–§7 for what each of these pieces means and how they are configured.

### 3. Get a token

Management-API calls need a bearer token. `scripts/token.sh` mints one:

```bash
TOKEN="$(./scripts/token.sh)"
curl -H "Authorization: Bearer $TOKEN" http://jad.localhost/api/management/...
```

It runs `kubectl create token` for a ServiceAccount and exchanges it (RFC 8693) at the `jwtlet` IDP
through the ingress, printing the resulting `access_token`. Override the defaults via the `KUBE_SA`,
`KUBE_NS`, `IDP_TOKEN_ENDPOINT` and `IDP_SCOPE` environment variables (see the script header).

### 3. Exercise it with Bruno

Import [`requests/EDC-V-Onboarding-Cx/`](./requests/EDC-V-Onboarding-Cx) into Bruno, select the
**KinD Local** environment (base URLs on `http://jad.localhost/...`), and paste the token from
step 2 into the collection's bearer auth. Run the folders in their numbered order:

1. **`CFM - Provision Tx / Consumer / Provider`** — provision the participants via the tenant-manager.
2. **`EDC-V Management` → `Prepare Consumer Participant`** — associate the `cx-neptune` profile and
   prepare the dataplane.
3. **`EDC-V Management` → `Prepare Provider Participant`** — create the certificate asset, the
   membership policy, the contract definition and the dataplane.
4. **`EDC-V Management` → `Data Transfer`** — the consumer flow: catalog request → contract
   negotiation → poll for the agreement → transfer → fetch the token → fetch the certificate.

Requests chain their outputs to the next request automatically (via `bru.setVar`). The certificate
you pull at the end is gated by the Membership CEL policy seeded in step 1 — so a successful transfer
exercises the profile end to end.

## Optional — Tractus-X connector integration

[`charts/tractusx/`](./charts/tractusx) (`tractusx-connector`) is a wrapper around the upstream
[`tractusx-connector`](https://github.com/eclipse-tractusx/tractusx-edc) `0.12.0` chart plus
`bdrs-server-memory` `0.6.0` (an in-cluster, in-memory BPN-DID Resolution Service). It lets a
Tractus-X EDC connector join the same dataspace and interoperate with the cx-profile-configured
EDC-V connector.

When running  the Tractus-X Connector integration the  we need to change a bit the core-platform
to configure the siglet with the tractus-x renewal protocol support:

```bash
helm upgrade -install core-platform oci://ghcr.io/eclipse-cfm/charts/core-platform-distribution \
		--version 0.0.7 \
    -f platform-override-values.yaml \
		--namespace edc-v --create-namespace --wait --timeout 15m
```


Then install the Tractus-X connector:

```bash
# set the changeme-* values first: participant.context.id,
# bdrs-server-memory.server.trustedIssuers, and the dataplane signer/verifier aliases
helm dependency build charts/tractusx
helm upgrade --install tractusx charts/tractusx/ \
                    --namespace edc-v \
                    --set participant.context.id=<participant-context-id> \
                    --wait --timeout 10m
```

Replace `<participant-context-id>` with the actual participant context ID that can be obtained from the CFM - Provision Tx output.

Its `tractusxSeed` job writes the required vault secrets and the `edc_sts_client` DB row. Once it is
up, run the **`CFM - Provision Tx`** folder and the **`Data Transfer` → `Http TractusX Provider`**
requests in the Bruno collection to fetch the catalog from the Tractus-X provider.

For the connector's own configuration options, see the upstream
[tractusx-edc](https://github.com/eclipse-tractusx/tractusx-edc) chart documentation.

## Verifying a connector — the cx-tck compatibility kit

[`cx-tck/`](./cx-tck) is a **Technology Compatibility Kit** for connectors participating in the
Catena-X dataspace. It checks that a connector correctly implements the combination of **DSP**
(Dataspace Protocol `2025-1`) exchange authorized with a **DCP** identity, by reusing the published
`dsp-tck` and `dcp-tck` artifacts rather than re-implementing either protocol.

It can run as a local self-test (in-memory connector, no identity) or against a real connector
under test — the [`charts/tractusx`](./charts/tractusx) connector above is a suitable target.
Running against a real connector requires publishing the datasets with the expected policies and
adding the TCK's embedded issuer DID to the connector's trusted issuers.

See [`cx-tck/README.md`](./cx-tck/README.md) for the full setup, configuration and the catalog /
flow / token-renewal test suites.

## Further reading

- [`neptune.md`](./neptune.md) — the full Catena-X profile specification.
- [cx-odrl-profile](https://github.com/catenax-eV/cx-odrl-profile) — Catena-X operands, sample
  policies, JSON Schemas.
- The Eclipse EDC decision records referenced in [`neptune.md`](./neptune.md) (dataspace profile
  context, multi-profile virtual connector, CEL expressions, dynamic DCP scopes, JSON Schema adoption).
