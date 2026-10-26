---
title: "2. Designing the Aurora profile"
description: Turn governance decisions into identifiers, credentials, DCP scopes, policy terms and CEL expressions.
weight: 20
---

<!-- TOC -->
* [The governance decisions](#the-governance-decisions)
* [Step 1: name the profile and pick the protocol](#step-1-name-the-profile-and-pick-the-protocol)
* [Step 2: decide who is trusted](#step-2-decide-who-is-trusted)
* [Step 3: design the credentials](#step-3-design-the-credentials)
* [Step 4: define the policy vocabulary](#step-4-define-the-policy-vocabulary)
* [Step 5: decide which credentials to request (DCP scopes)](#step-5-decide-which-credentials-to-request-dcp-scopes)
* [Step 6: write the CEL expressions](#step-6-write-the-cel-expressions)
* [Step 7: choose the identifiers](#step-7-choose-the-identifiers)
* [The profile on one page](#the-profile-on-one-page)
<!-- TOC -->

This chapter makes no API calls. It produces the **design** that chapters 04 through 08 implement. Make these
decisions for your own dataspace before you configure any software.

## The governance decisions

The Aurora governance body agreed on:

1. Aurora participants talk **DSP 2025-1** over HTTPS.
2. A single operator-run issuer certifies **membership**.
3. A participant can be certified as a **data processor** at one of two levels, `processing` or `sensitive`.
   Providers can restrict data to a level.
4. Providers express these rules as ODRL policies using a small Aurora vocabulary.

## Step 1: name the profile and pick the protocol

| Decision | Value |
|----------|-------|
| Profile name | `aurora-2025` |
| DSP version / binding / namespace | `2025-1` / `https` / `https://w3id.org/dspace/2025/1/` |
| JSON-LD contexts sent on the wire | `https://w3id.org/dspace/2025/1/context.jsonld`, `https://w3id.org/edc/dspace/v0.0.1` |

The profile name becomes part of every DSP URL (`/api/dsp/{participant}/aurora-2025`) and is the value of
`"protocol"` in catalog, negotiation and transfer requests.

> **Tip.** Put a version into the name. A breaking change to the dataspace rules then becomes a *new* profile, and
> participants can migrate at their own pace while the runtime serves both.

## Step 2: decide who is trusted

Credentials are only as good as their issuer. The profile lists its **trusted issuers** and the credential types
each one may issue:

| Issuer DID | Supported types |
|------------|-----------------|
| `did:web:issuer.cpd.localhost:issuer` | `MembershipCredential`, `DataProcessorCredential` |

A connector rejects credentials from any other issuer, or of any other type from this issuer, when it validates a
presentation.

## Step 3: design the credentials

Keep credentials small and stable. A credential states *facts* about the holder. Policies, not credentials, decide
what those facts allow.

| Credential type | `credentialSubject` | Where the values come from | Validity |
|-----------------|---------------------|----------------------------|----------|
| `MembershipCredential` | `id` (holder DID), `memberOf: "Aurora"` | holder registration properties | 7 days |
| `DataProcessorCredential` | `id`, `level: "processing" \| "sensitive"` | holder registration properties | 7 days |

"Holder registration properties" means the issuer copies the values from the properties stored with the holder
when it is onboarded. This is the IssuerService **holder attestation**. A production dataspace would more likely
use a **database attestation** that reads a membership registry. Chapter [04](../04-issuer) shows both.

Both credentials use the format `VC1_0_JWT`.

## Step 4: define the policy vocabulary

Policies need left operands. Aurora defines them under its own namespace, `https://w3id.org/aurora/policy/`:

| Left operand | Right operand | Meaning |
|--------------|---------------|---------|
| `https://w3id.org/aurora/policy/Membership` | `active` | The counterparty is an Aurora member |
| `https://w3id.org/aurora/policy/DataAccess.level` | `processing` \| `sensitive` | The counterparty is certified at exactly this level |

A provider combines these terms in its offers. For example, a document set for certified processors carries this
access policy:

```json
{ "and": [
  { "leftOperand": "https://w3id.org/aurora/policy/Membership",        "operator": "eq", "rightOperand": "active" },
  { "leftOperand": "https://w3id.org/aurora/policy/DataAccess.level", "operator": "eq", "rightOperand": "processing" }
]}
```

> **Optional.** A mature dataspace publishes a JSON-LD context, for example `https://w3id.org/aurora/policy/v1`, so
> policies can say `"leftOperand": "Membership"`. Add it to the profile's `jsonLdContextsUrl` and register it as a
> cached document (see chapter [05](../05-connector-profile#optional-json-ld-contexts)). This tutorial uses full IRIs to
> stay self-contained.

## Step 5: decide which credentials to request (DCP scopes)

During every DSP interaction the provider asks the consumer's credential service for a presentation. The
**DCP scopes** of the profile decide what is requested:

| Scope id | Value | Type |
|----------|-------|------|
| `aurora-membership-scope` | `org.eclipse.dspace.dcp.vc.type:MembershipCredential:read` | `DEFAULT` |
| `aurora-dataprocessor-scope` | `org.eclipse.dspace.dcp.vc.type:DataProcessorCredential:read` | `DEFAULT` |

There are two scope types:
- **`DEFAULT` scopes** are requested on every interaction under the profile.
- **`POLICY` scopes** are requested only when a policy being evaluated uses a left operand that matches the scope's
  `prefixMapping`.

`POLICY` scopes keep presentations minimal when a dataspace has many credential types. With two types, `DEFAULT` is
simpler.

## Step 6: write the CEL expressions

Each left operand gets exactly one expression:

| Expression id | Left operand | Expression |
|---------------|--------------|------------|
| `aurora-membership-expression` | `…/Membership` | `ctx.agent.claims.vc.withType('MembershipCredential').hasClaim('memberOf', 'Aurora')` |
| `aurora-dataaccess-level-expression` | `…/DataAccess.level` | `ctx.agent.claims.vc.withType('DataProcessorCredential').hasClaim('level', this.rightOperand)` |

Things to note:

- `ctx.agent.claims.vc` contains only credentials that passed signature checks *and* come from a trusted issuer for
  their type.
- The level expression compares against `this.rightOperand`, so one expression serves every level a provider might
  require.
- Both expressions apply to the `catalog`, `contract.negotiation` and `transfer.process` scopes.
  - In the catalog, a dataset whose access policy fails is simply **not shown**.
  - During negotiation and transfer, the expression is re-checked.
- You can add `.valid()` (for example `ctx.agent.claims.vc.valid().withType(...)`) to filter out expired or
  not-yet-valid credentials explicitly.

## Step 7: choose the identifiers

| Thing | Identifier |
|-------|------------|
| Issuer DID (minted by CPD) | `did:web:issuer.cpd.localhost:issuer` |
| Provider DID | `did:web:identity.cpd.localhost:aurora-provider` |
| Consumer DID | `did:web:identity.cpd.localhost:aurora-consumer` |
| Issuer credential definition ids | `aurora-membership-def`, `aurora-dataprocessor-def` |
| Provider certification level | `sensitive` |
| Consumer certification level | `processing` |

IdentityHub serves DID documents on its own hostname and looks them up by **exact DID**. The last DID segment, such
as `aurora-provider`, is therefore free to choose, and it does **not** have to equal the participant context id.
That matters for CFM, which generates the context id only during onboarding.

## The profile on one page

Here is the whole Aurora design and where each part is implemented:

| Element | Implemented in | Payload |
|---------|----------------|---------|
| Credential definitions + attestation | IssuerService (ch. 04) | [`payloads/issuer/`](../assets/payloads/issuer) |
| Profile, protocol, trusted issuer | Control plane (ch. 05) | [`dataspaceprofile.json`](../assets/payloads/connector/dataspaceprofile.json) |
| DCP scopes | Control plane (ch. 05) | [`dcpscope-*.json`](../assets/payloads/connector) |
| CEL expressions | Control plane (ch. 05) | [`celexpression-*.json`](../assets/payloads/connector) |
| Credentials every participant receives | CFM dataspace profile (ch. 06) or credential request (ch. 07) | [`cfm/dataspace-profile.json`](../assets/payloads/cfm/dataspace-profile.json) |
| Policies using the vocabulary | Each provider (ch. 08) | [`payloads/provider/`](../assets/payloads/provider) |
