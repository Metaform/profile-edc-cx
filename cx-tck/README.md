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
| `cx-api`     | Shared API: the CX annotations, `CxFunctions`/`DatasetQuery` catalog discovery, `CxPolicies`, and the `Edr` + `DataPlaneRequests` use-case primitives. |
| `cx-system`  | `CxSystemLauncher` — the composing launcher (DSP exchange + DCP identity).                                                                     |
| `cx-catalog` | The catalog test cases (`CxCatalog01Test`), discovered by package scan.                                                                        |
| `cx-flow`    | The end-to-end flow test cases: `CxFlow01Test` (catalog → negotiation → transfer) and `CxRenewalFlow01Test` (transfer → OAuth2 token renewal). |
| `cx-usecase` | The foundation for verifying Catena-X **use-case** standards above DSP: `AbstractCxUseCaseTest` (EDR establishment) and JSON-Schema validation. |
| `cx-ccm`     | CX-0135 Company Certificate Management test cases (`CxCcm00Test`…`CxCcm03Test`), currently covering the v2.4.0 push.                           |
| `cx-tck`     | The runnable suite (`CxTckSuite`), packaged as `cx-tck-runtime.jar`.                                                                           |

### Verifying a use case

The suites above verify the *connector*. A Catena-X **use-case** standard sits on top: it defines an
application API and the messages exchanged over it, and leaves reaching that API to DSP. `cx-usecase`
is the layer that expresses this — `AbstractCxUseCaseTest.establishEdr(...)` drives catalog →
negotiation → transfer and hands back an `Edr` (endpoint + token), and `DataPlaneRequests` calls the
application API through it.

Two things differ from the connector suites and are worth knowing before writing a use-case test:

- **Assets are discovered by property, not by id.** Use-case standards classify their assets with
  taxonomy properties (`dct:type`, `dct:subject`, `cx-common:version`), so a test describes the asset
  it needs with a `DatasetQuery` instead of being handed a configured dataset id. `CxFunctions`
  handles the expanded-JSON-LD shapes a connector may emit, including the `@list` wrapper EDC puts
  around array-valued properties.
- **Payloads are checked against the standard's own JSON Schema.** `JsonSchemas` validates
  application messages against schemas shipped on the classpath. Schemas are never fetched over the
  network — an in-cluster run may have no egress at all — so any externally referenced schema is
  vendored (see [Vendored schemas](#vendored-schemas)).

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

### CCM Usage Policy (CX-0135)

The Company Certificate Management offer must additionally carry the CCM usage purpose. This is the
value most often wrong in a deployment: another use case's purpose (`cx.pcf.base:1` and friends) is
easy to copy across, and nothing else in the exchange notices — only `CX_CCM:01-02` does.

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
              "leftOperand": "FrameworkAgreement",
              "operator": "eq",
              "rightOperand": "DataExchangeGovernance:1.0"
            },
            {
              "leftOperand": "UsagePurpose",
              "operator": "isAnyOf",
              "rightOperand": "cx.ccm.base:1"
            }
          ]
        }
      ]
    }
  ]
}
```

## Required CCM asset

For the CX-0135 suites the TCK acts as the **Certificate Provider** and the connector under test as
the **Certificate Consumer**. CX-0135 splits its four endpoints across two EDC assets by role, and a
Certificate Consumer is the side that offers `/companycertificate/push` and
`/companycertificate/available` — so the connector under test must publish a notification API asset
carrying the §2.1.4.1 classification:

```json
{
  "@context": [
    "https://w3id.org/edc/connector/management/v2",
    {
      "cx-common": "https://w3id.org/catenax/ontology/common#",
      "cx-taxo": "https://w3id.org/catenax/taxonomy#",
      "dct": "http://purl.org/dc/terms/"
    }
  ],
  "@type": "Asset",
  "@id": "ccm_api_asset",
  "dataAddress": {
    "@type": "DataAddress",
    "type": "HttpData",
    "baseUrl": "<base url of the CX-0135 v2.4.0 consumer implementation>",
    "proxyPath": "true",
    "proxyMethod": "true",
    "proxyBody": "true"
  },
  "properties": {
    "dct:type": { "@id": "cx-taxo:CCMAPI" },
    "dct:subject": { "@id": "cx-taxo:CompanyCertificateManagementNotificationApi" },
    "cx-common:version": "3.0"
  }
}
```

> **`proxyPath`, `proxyMethod` and `proxyBody` must all be `true`.** With the defaults the data plane
> rewrites the TCK's `POST /companycertificate/push` into a bodyless `GET` of the base URL, so the
> push never reaches the notification API and every `CX_CCM:02-*` test fails for a reason that has
> nothing to do with CX-0135. The suite detects a 404 or 405 and says so explicitly.

The **certificates themselves are shipped inside the TCK** and sent on the wire, so nothing else has
to be seeded: no certificate assets, no certificate store, no fixture server.

## Vendored schemas

`cx-ccm/src/main/resources/ccm/v240/schema/business-partner-certificate-3.1.0-schema.json` is a
checked-in copy of the published `io.catenax.business_partner_certificate` 3.1.0 JSON Schema, taken
from `eclipse-tractusx/sldt-semantic-models`. The CX-0135 API document references it by URL; resolving
that at validation time would make a conformance run depend on an external host being reachable, which
an in-cluster Job may not be. The file records its source commit and date in a `$comment`; refresh it
from upstream when the semantic model is versioned. `CcmSchemaTest` asserts no schema under
`ccm/v240/` references a remote document.

## Status

Connector suites combining DSP exchange with DCP identity, plus a use-case suite verifying
CX-0135 Company Certificate Management on top of them.

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

### Company Certificate Management, CX-0135 v2.4.0 (`cx-ccm`)

Verifies a **CX-0135 Certificate Consumer**. The TCK plays the Certificate Provider: it discovers the
connector's notification API asset by its taxonomy properties, negotiates a contract, starts a
transfer, and delivers a `BusinessPartnerCertificate` 3.1.0 through the resulting EDR. See
[Required CCM asset](#required-ccm-asset) for what the connector must publish.

#### Suite self-verification (`CxCcm00Test`)

| Test ID        | Verifies                                                                                                          | Expected result |
|----------------|-------------------------------------------------------------------------------------------------------------------|-----------------|
| `CX_CCM:00-01` | The certificates and messages the TCK will send satisfy the CX-0135 v2.4.0 schemas, and a malformed one does not | Pass            |

Needs no connector, so this is the one CCM test that **runs in the local self-test** — which is what
makes the self-test meaningful for this suite: it proves the module is on the runtime classpath and
that the payloads the TCK puts on the wire are themselves conformant.

#### Asset and policy conformance (`CxCcm01Test`)

Catalog-only — no negotiation, no transfer. The cheapest signal in the suite, and the first thing to
check when a push test fails.

| Test ID        | Verifies                                                                                                                        | Expected result | Spec      |
|----------------|-----------------------------------------------------------------------------------------------------------------------------------|-----------------|-----------|
| `CX_CCM:01-01` | The notification API asset carries `dct:type cx-taxo:CCMAPI`, `dct:subject cx-taxo:CompanyCertificateManagementNotificationApi` and `cx-common:version` | Asset found and classified | §2.1.4.1 |
| `CX_CCM:01-02` | Its offer constrains `UsagePurpose isAnyOf cx.ccm.base:1` **and** `FrameworkAgreement eq DataExchangeGovernance:1.0`            | Both constraints present | §2.1.7 |
| `CX_CCM:01-03` | No two CCM API datasets share a (`dct:subject`, `cx-common:version`) pair                                                       | No duplicates   | §2.1.4.1 |

`CX_CCM:01-03` is a partial check: §2.1.4.1 states the rule across *all connectors of one BPNL*, and a
TCK observes a single catalog, so it can only verify uniqueness within the catalog under test.

#### Embedded certificate push (`CxCcm02Test`)

Each case differs only in the certificate it carries. Together they span the payload dimensions the
official CX-0135 test cases distinguish, so they show the receiver handles the whole 3.1.0 shape
rather than only the easy case.

| Test ID        | Certificate pushed                                    | Expected result | Why it is distinct                                                        |
|----------------|-------------------------------------------------------|-----------------|---------------------------------------------------------------------------|
| `CX_CCM:02-01` | ISO 9001, one enclosed site, currently valid          | `2xx`           | the baseline delivery                                                     |
| `CX_CCM:02-02` | `enclosedSites` mixing one BPNS with two BPNA         | `2xx`           | 3.1.0 widened `enclosedSites` to accept BPNA; a BPNS-only receiver fails here |
| `CX_CCM:02-03` | `validUntil` in the past                              | `2xx`           | expiry is certificate data, not a transport condition — v2.4.0 gives no way to decline delivery over it |
| `CX_CCM:02-04` | `validUntil` = `9999-12-31`                           | `2xx`           | a date parser that rejects year 9999 fails here and nowhere else          |

The assertion is `2xx`, not exactly `200` with an empty body: CX-0135 specifies 200/no body, but a
receiver that returns an acknowledgement body is not non-conformant in a way worth failing over.

#### Push envelope error handling (`CxCcm03Test`)

| Test ID        | Message sent                                     | Expected result |
|----------------|--------------------------------------------------|-----------------|
| `CX_CCM:03-01` | header omits `messageId`                         | not `2xx`       |
| `CX_CCM:03-02` | `senderBpn` is not a BPNL                        | not `2xx`       |
| `CX_CCM:03-03` | `header.context` is the Status context           | not `2xx`       |
| `CX_CCM:03-04` | no certificate content                           | not `2xx`       |

**Not mandatory, deliberately.** CX-0135 v2.4.0 documents only `200` and `500` for the push endpoint —
`400` appears solely on `/companycertificate/request` — so the standard does not oblige a receiver to
answer a malformed push with any particular status. These tests therefore assert only that a message
violating the specification's own schema is not acknowledged as accepted, and report the status that
came back. A conformance suite should not invent requirements the standard does not state; the gap is
worth raising with the standardisation body instead.

#### Not covered

- **The `/companycertificate/status` feedback leg.** The official cases TC-CCM-03/04/05 continue with
  the consumer marking the certificate accepted or rejected and reporting back. That message travels
  consumer → provider, so it arrives *at* the TCK, which requires the TCK to act as a DSP provider.
  Consequently those three cases are covered here only up to the delivery.
- **`/companycertificate/available`.** Same asset and same direction as the push — the natural next
  increment.
- **Verifying a CX-0135 Certificate *Provider*.** That is the mirror direction: the TCK would receive
  the push, which needs TCK-as-DSP-provider plus a way to tell the provider to publish — something
  CX-0135 does not define.

Follow-ups: transfer completion and the remaining Catena-X profile specifics (CEL policy operands,
JSON-Schema policy validation) described in [`../neptune.md`](../neptune.md).
