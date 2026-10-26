---
title: "5. Configuring the connector"
description: Register the Aurora dataspace profile with its trusted issuer, DCP scopes and CEL expressions.
weight: 50
---

<!-- TOC -->
* [Register the dataspace profile](#register-the-dataspace-profile)
* [Trusted issuers](#trusted-issuers)
* [DCP scopes](#dcp-scopes)
* [CEL expressions](#cel-expressions)
* [Optional: JSON-LD contexts](#optional-json-ld-contexts)
* [Run it](#run-it)
* [Putting it together](#putting-it-together)
* [Related](#related)
<!-- TOC -->

Script: [`03-connector-profile.sh`](../assets/scripts/03-connector-profile.sh). Payloads:
[`payloads/connector/`](../assets/payloads/connector).

This chapter configures the **control plane runtime**, not an individual participant. The profile, its scopes and
its expressions are shared by every participant context that is associated with `aurora-2025`. All calls go to the
Management API at `http://cpd.localhost/api/management/v5/...` with the operator token
(`management-api:admin`).

> **API versions.** CPD's control plane serves these endpoints under `/v5`. The EDC documentation currently shows
> them under `/v5beta`. The payloads are the same.

## Register the dataspace profile

[`dataspaceprofile.json`](../assets/payloads/connector/dataspaceprofile.json):

```json
{
  "@context": ["https://w3id.org/edc/connector/management/v2"],
  "@type": "DataspaceProfile",
  "name": "aurora-2025",
  "protocol": {
    "version": "2025-1",
    "binding": "https",
    "namespace": "https://w3id.org/dspace/2025/1/"
  },
  "jsonLdContextsUrl": [
    "https://w3id.org/dspace/2025/1/context.jsonld",
    "https://w3id.org/edc/dspace/v0.0.1"
  ],
  "trustedIssuers": [
    { "@id": "did:web:issuer.cpd.localhost:issuer",
      "supportedTypes": ["MembershipCredential", "DataProcessorCredential"] }
  ]
}
```

| Operation | Request | Notes |
|-----------|---------|-------|
| Create | `POST /v5/dataspaceprofiles` | `409` if the name exists |
| Update | `PUT /v5/dataspaceprofiles` | `404` if it does not exist; the script upserts |
| Read | `GET /v5/dataspaceprofiles/{name}` | |

After registration the control plane serves DSP for `aurora-2025` at `/api/dsp/{participantContextId}/aurora-2025`.
It does so only for participant contexts **associated** with the profile. That association is part of onboarding
(`PUT /v5/participants/{id}/profiles`, chapters 06 and 07).

## Trusted issuers

Trust is part of the profile: the `trustedIssuers` list in the payload above.
- Credentials are trusted only within interactions under this profile, and only for the listed `supportedTypes`
  (`["*"]` means any type).
- Because trust is scoped per profile, one runtime can serve several dataspaces that trust different issuers.
- To add or remove an issuer, or change the types it may issue, `PUT` the updated profile. No restart is needed.

During presentation verification, a credential that is signed by an untrusted issuer, or whose type is not
supported for its issuer, is dropped. It never reaches `ctx.agent.claims.vc`, so no CEL expression can match it.

## DCP scopes

DCP scopes tell the control plane which credentials to request when it queries a counterparty's credential
service ([`dcpscope-membership.json`](../assets/payloads/connector/dcpscope-membership.json)):

```json
{
  "@context": ["https://w3id.org/edc/connector/management/v2"],
  "@type": "DcpScope",
  "@id": "aurora-membership-scope",
  "type": "DEFAULT",
  "value": "org.eclipse.dspace.dcp.vc.type:MembershipCredential:read",
  "profile": "aurora-2025"
}
```

| Operation | Request |
|-----------|---------|
| Create | `POST /v5/dcpscopes` |
| Update / delete | `PUT` / `DELETE /v5/dcpscopes/{id}` |
| Query | `POST /v5/dcpscopes/request` |

- `value` follows the DCP grammar `<alias>:<discriminator>:<operation>`. With alias
  `org.eclipse.dspace.dcp.vc.type` the discriminator is a credential **type**. The holder's IdentityHub maps it to a
  query over its stored credentials.
- `profile` limits the scope to interactions under `aurora-2025`. A scope without a profile applies to every
  profile.
- `type: POLICY` with a `prefixMapping` requests the credential only when a policy that uses a matching left operand
  is evaluated.

> **Interop note.** Holders that use a different scope alias, such as Tractus-X connectors with
> `org.eclipse.tractusx.vc.type:…`, need a matching mapping in IdentityHub. In CPD that is
> `edc.identityhub.scopes`, which takes a `pattern` and maps it to a credential query (`leftOperand`, `operator`,
> `rightOperand`). [`platform-override-values.yaml`](../../platform-override-values.yaml) in this repository has an
> example.

## CEL expressions

One expression per Aurora left operand
([`celexpression-membership.json`](../assets/payloads/connector/celexpression-membership.json),
[`celexpression-dataaccess-level.json`](../assets/payloads/connector/celexpression-dataaccess-level.json)):

```json
{
  "@context": ["https://w3id.org/edc/connector/management/v2"],
  "@type": "CelExpression",
  "@id": "aurora-dataaccess-level-expression",
  "leftOperand": "https://w3id.org/aurora/policy/DataAccess.level",
  "scopes": ["catalog", "contract.negotiation", "transfer.process"],
  "expression": "ctx.agent.claims.vc.withType('DataProcessorCredential').hasClaim('level', this.rightOperand)"
}
```

| Operation | Request |
|-----------|---------|
| Create | `POST /v5/celexpressions` |
| Read / update / delete | `GET` / `PUT` / `DELETE /v5/celexpressions/{id}` |
| Query | `POST /v5/celexpressions/request` |
| Dry-run | `POST /v5/celexpressions/test` |

The binding is the **left operand IRI**. Any policy constraint whose `leftOperand` expands to
`https://w3id.org/aurora/policy/DataAccess.level` is evaluated with this expression.
- `scopes` restricts the policy scopes the expression is registered in.
- `actions` and `supportedOperators` (not used here) narrow it further.
- If several expressions share a left operand, they are ANDed.

Useful helpers on `ctx.agent.claims.vc`:

| Helper | Meaning |
|--------|---------|
| `withType(t)`, `withIssuer(did)`, `withContext(iri)` | filter the credential list |
| `valid()` | keep only credentials within their validity period |
| `hasCredential()` | at least one credential left after filtering |
| `hasClaim(name)`, `hasClaim(name, value)` | some credential has the subject claim (with that value) |
| `claim(name)`, `claims(name)` | the value of a claim / all values, e.g. `claims('level').exists(l, l in ['processing','sensitive'])` |

[`celexpression-test.json`](../assets/payloads/connector/celexpression-test.json) dry-runs an expression against
the `this` binding with `POST /v5/celexpressions/test`. That lets you check syntax and logic before any policy uses
it.

## Optional: JSON-LD contexts

If Aurora publishes a policy context, for example `https://w3id.org/aurora/policy/v1` defining `Membership`, add it
to `jsonLdContextsUrl`. Then cache it so the runtime never fetches it from the network at request time:

```json
{
  "@context": ["https://w3id.org/edc/connector/management/v2"],
  "@type": "CachedDocument",
  "url": "https://w3id.org/aurora/policy/v1",
  "documentType": "JSON_LD",
  "content": { "@context": { "aurora": "https://w3id.org/aurora/policy/", "Membership": "aurora:Membership" } }
}
```

```bash
POST /v5/cacheddocuments          # omit "content" to let the runtime download the URL
```

## Run it

```bash
tutorial/assets/scripts/03-connector-profile.sh
```

## Putting it together

- [ ] `aurora-2025` registered, with the issuer trusted for both credential types
- [ ] DCP scopes for `MembershipCredential` and `DataProcessorCredential`, bound to the profile
- [ ] CEL expressions for `…/Membership` and `…/DataAccess.level`
- [ ] `advertisedProfile` in CPD equals `aurora-2025` (chapter 03)

## Related

- [Dataspace Profiles](https://eclipse-edc.github.io/documentation/for-adopters/control-plane/dataspace-profiles/)
- [CEL Policy Expressions](https://eclipse-edc.github.io/documentation/for-adopters/control-plane/policy-engine/cel/)
- [Policy Engine](https://eclipse-edc.github.io/documentation/for-adopters/control-plane/policy-engine/)
