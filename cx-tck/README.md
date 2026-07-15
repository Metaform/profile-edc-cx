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

| Module       | Purpose                                                                    |
|--------------|----------------------------------------------------------------------------|
| `cx-system`  | `CxSystemLauncher` — the composing launcher (DSP exchange + DCP identity).  |
| `cx-catalog` | The catalog test cases (`CxCatalog01Test`), discovered by package scan.     |
| `cx-flow`    | The end-to-end flow test cases (`CxFlow01Test`): catalog → negotiation → transfer. |
| `cx-runtime` | The runnable suite (`CxTckSuite`), packaged as `cx-tck-runtime.jar`.        |

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

## Status

Two suites of mandatory test cases, both combining DSP exchange with DCP identity.

### Catalog requests (`CxCatalog01Test`)

| Test ID        | Verifies                                                                          | Expected result |
|----------------|-----------------------------------------------------------------------------------|-----------------|
| `CX_CAT:01-01` | Catalog request authorized with a DCP identity (Membership + BPN + Gov credentials) | Catalog returned |
| `CX_CAT:01-02` | Catalog request authorized with a DCP identity presented with the **wrong scopes**  | `401` catalog error |
| `CX_CAT:01-03` | Catalog request authorized with a DCP identity **missing required credentials**     | `401` catalog error |
| `CX_CAT:01-04` | Catalog request against a dataset carrying a **BPN access restriction**              | Catalog returned |
| `CX_CAT:01-05` | Catalog request that **filters out** a BPN-restricted dataset for a non-matching BPN | Restricted dataset filtered out |

### End-to-end flows (`CxFlow01Test`)

Whole-flow tests that chain **catalog → contract negotiation → transfer** in a single exchange,
driven by the TCK as the consumer against the connector under test as the provider. The offer that
is negotiated is the **real offer extracted from the catalog** response (`CxFunctions`), and the
negotiated agreement id is carried into the transfer.

| Test ID         | Verifies                                                                                                                     | Expected result |
|-----------------|------------------------------------------------------------------------------------------------------------------------------|-----------------|
| `CX_FLOW:01-01` | Catalog fetch → contract negotiation to `FINALIZED` → transfer to `STARTED`, extracting the data address from the `TransferStartMessage` | Transfer started; data address present |
| `CX_FLOW:01-02` | Contract request for an offer whose contract policy the identity cannot satisfy (non-matching `DataExchangeGovernance` contract version) | `401` on the contract request |

`CX_FLOW:01-02` exercises real contract-policy enforcement, so it is **skipped in the local
self-test** (the in-memory connector does not evaluate contract policy) and runs only against a real
connector under test.

Follow-ups: per-request token minting, transfer completion, and the remaining Catena-X profile
specifics (CEL policy operands, JSON-Schema policy validation) described in
[`../neptune.md`](../neptune.md).
