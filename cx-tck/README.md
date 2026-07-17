# cx-tck — Catena-X Technology Compatibility Kit

A Technology Compatibility Kit (TCK) for connectors participating in the **Catena-X** dataspace.

Catena-X uses two protocols together:

- **DSP** (Dataspace Protocol, version `2025-1`) — the data-exchange protocol (catalog,
  contract negotiation, transfer).
- **DCP** (Decentralized Claims Protocol) — the identity / authorization mechanism (DID
  resolution, self-issued tokens, verifiable-presentation exchange).

`cx-tck` verifies that a connector correctly implements the **combination** of the two: a DSP
request authorized with a DCP identity. It does this by **reusing the published
[dsp-tck](https://github.com/eclipse-dataspacetck/dsp-tck) and
[dcp-tck](https://github.com/eclipse-dataspacetck/dcp-tck) artifacts** rather than
re-implementing either protocol.

## How it combines DSP and DCP

Both TCKs are built on the same protocol-agnostic harness
(`org.eclipse.dataspacetck.common:*`, JUnit 5 driven by `TckRuntime`, a `SystemLauncher` for
dependency injection, and an embedded callback HTTP server). The framework runs a single
launcher, so cx-tck provides a composing one:

`org.eclipse.dataspacetck.cx.system.CxSystemLauncher`

- **Exchange:** delegates all DSP service injection (`CatalogClient`, `Connector`, …) to the
  dsp-tck `DspSystemLauncher`.
- **Identity:** reuses the dcp-tck `BaseAssembly` / `ServiceAssembly` fixtures to host the
  holder DID document, the CredentialService (presentation-query endpoint) and the Secure
  Token Server on the shared callback endpoint.
- **Wiring:** instead of the static bearer token dsp-tck would attach, cx-tck mints a **DCP
  self-issued token** (an ID token signed by the holder key, carrying an STS access token in the
  `token` claim) and registers it as the DSP `Authorization` header via the dsp-tck
  `HttpFunctions` interceptor. When the connector under test verifies the request, it resolves
  the holder DID and calls back to the hosted CredentialService — the DCP presentation-query
  flow.

## Modules

| Module       | Purpose                                                                                                                                        |
|--------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| `cx-system`  | `CxSystemLauncher` — the composing launcher (DSP exchange + DCP identity).                                                                     |
| `cx-catalog` | The catalog test cases (`CxCatalog01Test`), discovered by package scan.                                                                        |
| `cx-flow`    | The end-to-end flow test cases: `CxFlow01Test` (catalog → negotiation → transfer) and `CxRenewalFlow01Test` (transfer → OAuth2 token renewal). |
| `cx-runtime` | The runnable suite (`CxTckSuite`), packaged as `cx-tck-runtime.jar`.                                                                           |

## Building

Requires JDK 17 (matching the shared `dataspacetck` harness). Dependencies resolve from Maven
Central (the shared harness), the Sonatype snapshot repository (dsp-tck / dcp-tck) and
`mavenLocal`.

```bash
./gradlew build          # compile + run the local self-test
./gradlew shadowJar      # produce cx-runtime/build/libs/cx-tck-runtime.jar
```

## Running

The suite is configured via a `.tck.properties` file (see
[`config/tck/sample.tck.properties`](config/tck/sample.tck.properties)).

### Local self-test (no connector required)

With `dataspacetck.dsp.local.connector=true`, the catalog request is served by an in-memory
connector, exercising the harness end-to-end without HTTP or identity:

```bash
java -jar cx-runtime/build/libs/cx-tck-runtime.jar -config config/tck/sample.tck.properties
```

### Against a real Catena-X connector

Set `dataspacetck.dsp.local.connector=false`, point `dataspacetck.dsp.connector.*` at the
connector under test, and configure the DCP identity (`dataspacetck.callback.address`, the
`did:web` DIDs, and `dataspacetck.cx.provider.did`). The suite then issues a DSP catalog
request authorized with a DCP self-issued token, and the connector verifies it via the
presentation-query callback. The
[`tractusx`](../charts/tractusx) Helm chart in this repository deploys a suitable connector.

### In-cluster run via Helm

The [`charts/cx-tck`](../charts/cx-tck) chart runs the suite entirely inside the cluster as a
one-shot `Job`, using the published `ghcr.io/metaform/cx-tck-runtime:latest` image — no local JVM
and no mirrord. It renders the same properties as `CxTckSuiteRemoteTest` into a ConfigMap
(`/etc/tck/config.properties`) and creates the `cx-tck` Service that hosts the TCK callback and its
`did:web` documents, so the connector under test can call back and resolve the TCK identity. It
replaces the `cx-tck/mock.yaml` + `.mirrord/mirrord.json` developer flow for CI-style runs.

```bash
# The connector under test must trust did:web:cx-tck.<ns>.svc.cluster.local:issuer and publish the
# datasets/policies below. Set participantId to the connector's participant context id.
helm install cx-tck ../charts/cx-tck -n edc-v \
  --set tck.participantId=<participant-context-id>

kubectl logs -f job/cx-tck -n edc-v
```

Configure the run through `charts/cx-tck/values.yaml` (connector coordinates, DIDs, BPN, dataset
ids, and the `tck.keyJwk` holder/issuer key — override the sample key for real runs).

> **Reading the result.** The runtime writes its pass/fail summary to stdout and prints
> `Test run complete`, but the container **always exits `0`** even when tests fail. So the Job/pod
> always shows `Complete` — judge the outcome from the result summary in `kubectl logs`, not from the
> pod status. Re-run with `helm uninstall cx-tck && helm install …` (a `Job` spec is immutable, so a
> plain `helm upgrade` over an existing run is not supported).

### Trusted issuer

The credentials the TCK presents (Membership, BPN and DataExchangeGovernance) are **issued and
signed by an issuer embedded in the TCK itself**. Its DID is configured with:

```properties
dataspacetck.did.issuer=did:web:localhost%3A8083:issuer
```

If omitted, it is derived from `dataspacetck.callback.address` as `did:web:<host>:issuer`. The TCK
hosts the corresponding `did:web` document (and its signing key) on the callback endpoint, so the
connector under test can resolve the issuer DID and verify the credential signatures.

For that verification to succeed, **the connector under test must be configured to trust this issuer
DID** — add `dataspacetck.did.issuer` to the connector's list of **trusted issuers**. If the issuer
is not trusted, the connector rejects the presented credentials and every test that relies on a
credential (all catalog, flow and renewal tests) fails during identity verification.

> When the TCK runs inside the cluster, the issuer DID uses the in-cluster callback host, e.g.
> `did:web:cx-tck.edc-v.svc.cluster.local:issuer`. The value added to the connector's trusted
> issuers must match exactly the `dataspacetck.did.issuer` the TCK is run with.

## Required policies

To run the suite against a real connector under test, the datasets referenced by the tests must be
published with the policies below. The connector evaluates these policies against the DCP identity
the TCK presents, so they determine whether a catalog entry is visible, a contract can be negotiated,
and a transfer can start.

The leftOperand should be namespaced with `https://w3id.org/catenax/2025/9/policy/`

The action may vary on the use case which for access policy may be `access`

### Membership Policy

```json
{
  "@type": "Set",
  "permission": [
    {
      "action": "use",
      "constraint": [
        {
          "leftOperand": "Membership",
          "operator": "eq",
          "rightOperand": "active"
        }
      ]
    }
  ]
}
```

### BPN Policy

```json
{
  "@type": "Set",
  "permission": [
    {
      "action": "use",
      "constraint": [
        {
          "and": [
            {
              "leftOperand": "Membership",
              "operator": "eq",
              "rightOperand": "active"
            },
            {
              "leftOperand": "BusinessPartnerNumber",
              "operator": "eq",
              "rightOperand": "<BPN>"
            }
          ]
        }
      ]
    }
  ]
}
```

where `<BPN>` is the BPN of TCK connector configured in the TCK properties as `dataspacetck.cx.bpn`.

### DataExchangeGovernance Policy

```json
{
  "@type": "Set",
  "permission": [
    {
      "action": "use",
      "constraint": [
        {
          "and": [
            {
              "leftOperand": "Membership",
              "operator": "eq",
              "rightOperand": "active"
            },
            {
              "leftOperand": "FrameworkAgreement",
              "operator": "eq",
              "rightOperand": "DataExchangeGovernance:1.0"
            }
          ]
        }
      ]
    }
  ]
}
```

## Status

Three suites of mandatory test cases, all combining DSP exchange with DCP identity.

### Catalog requests (`CxCatalog01Test`)

| Test ID        | Verifies                                                                             | Expected result                 | Access Policy     | Contract Policy |
|----------------|--------------------------------------------------------------------------------------|---------------------------------|-------------------|-----------------|
| `CX_CAT:01-01` | Catalog request authorized with a DCP identity (Membership + BPN + Gov credentials)  | Catalog returned                | Memberhisp Policy | Any policy      |
| `CX_CAT:01-02` | Catalog request authorized with a DCP identity presented with the **wrong scopes**   | `401` catalog error             | Any policy        | Any policy      |
| `CX_CAT:01-03` | Catalog request authorized with a DCP identity **missing required credentials**      | `401` catalog error             | Any policy        | Any policy      |
| `CX_CAT:01-04` | Catalog request against a dataset carrying a **BPN access restriction**              | Catalog returned                | BPN Policy        | Any policy      |
| `CX_CAT:01-05` | Catalog request that **filters out** a BPN-restricted dataset for a non-matching BPN | Restricted dataset filtered out | BPN Policy        | Any policy      |

For `CX_CAT:01-04` and `CX_CAT:01-05`, the BPN in the policy must match the TCK connector's BPN
(`dataspacetck.cx.bpn`) and it's advised to use the same dataset for both tests, so that the connector can be configured
with a single dataset carrying the BPN policy.

### End-to-end flows (`CxFlow01Test`)

Whole-flow tests that chain **catalog → contract negotiation → transfer** in a single exchange,
driven by the TCK as the consumer against the connector under test as the provider. The offer that
is negotiated is the **real offer extracted from the catalog** response (`CxFunctions`), and the
negotiated agreement id is carried into the transfer.

| Test ID         | Verifies                                                                                                                                 | Expected result                        | Access Policy     | Contract Policy               |
|-----------------|------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------|-------------------|-------------------------------|
| `CX_FLOW:01-01` | Catalog fetch → contract negotiation to `FINALIZED` → transfer to `STARTED`, extracting the data address from the `TransferStartMessage` | Transfer started; data address present | Membership Policy | DataExchangeGovernance Policy |
| `CX_FLOW:01-02` | Contract request for an offer whose contract policy the identity cannot satisfy (non-matching `DataExchangeGovernance` contract version) | `401` on the contract request          | Membership Policy | DataExchangeGovernance Policy |

`CX_FLOW:01-02` exercises real contract-policy enforcement, so it is **skipped in the local
self-test** (the in-memory connector does not evaluate contract policy) and runs only against a real
connector under test. The tck connector present a DataExchangeGovernance credential with a version
`not-matching-version` to trigger the contract rejection.

### Token renewal flows (`CxRenewalFlow01Test`)

Each test first drives the base flow (**catalog → contract negotiation → transfer to `STARTED`**)
exactly like `CX_FLOW:01-01`, then exercises the DSP **Token Renewal profile**. It reads the
renewal parameters carried in the transfer-start data address (`refreshEndpoint`, `refreshToken`
and the current `authorization` access token, via `CxFunctions`) and performs an OAuth2
**refresh-token grant** ([RFC 6749 §6](https://www.rfc-editor.org/rfc/rfc6749#section-6)): a
form-encoded `POST refreshEndpoint` with `grant_type=refresh_token&refresh_token=…`.

The renewal request is authorized with a **DCP self-issued token** minted through the injected
`SelfIssuedTokenProvider` — the same identity mechanism used for the DSP exchange, but carrying the
data address's current access token in the `token` claim so the client authentication is bound to
the original grant.

| Test ID                 | Verifies                                                                                                                | Expected result                         |
|-------------------------|-------------------------------------------------------------------------------------------------------------------------|-----------------------------------------|
| `CX_RENEWAL_FLOW:01-01` | Full flow, then a `refresh_token` grant at the data address's `refreshEndpoint` authorized with a DCP self-issued token | `200` with a new `access_token`         |
| `CX_RENEWAL_FLOW:01-02` | Full flow, then a `refresh_token` grant presenting a syntactically valid but **unknown refresh token**                  | Grant rejected (`4xx`, `invalid_grant`) |

Both cases require a real DCP identity and real renewal properties in the data address, neither of
which the in-memory connector provides, so they are **skipped in the local self-test** and run only
against a real connector under test.

Follow-ups: transfer completion and the remaining Catena-X profile specifics (CEL policy operands,
JSON-Schema policy validation) described in [`../neptune.md`](../neptune.md).
