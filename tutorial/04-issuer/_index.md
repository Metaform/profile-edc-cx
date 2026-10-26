---
title: "4. Preparing the issuer"
description: Create the attestation and credential definitions that the Aurora IssuerService issues from.
weight: 40
---

<!-- TOC -->
* [The issuer participant context](#the-issuer-participant-context)
* [Attestation definitions: where claims come from](#attestation-definitions-where-claims-come-from)
* [Credential definitions: what gets issued](#credential-definitions-what-gets-issued)
* [Holders: who may ask](#holders-who-may-ask)
* [Run it](#run-it)
* [Putting it together](#putting-it-together)
* [Related](#related)
<!-- TOC -->

Script: [`02-issuer.sh`](../assets/scripts/02-issuer.sh). Payloads: [`payloads/issuer/`](../assets/payloads/issuer).

## The issuer participant context

The IssuerService is multi-tenant like every other EDC-V component. CPD already created the issuer's participant
context, with:
- id `issuer`;
- DID `did:web:issuer.cpd.localhost:issuer`;
- an EdDSA signing key;
- an `IssuerService` service endpoint, `http://cpd.localhost/api/issuance/v1/participants/issuer`, in its DID
  document.

Every Admin API call in this chapter goes to

```
http://cpd.localhost/api/issuer/admin/participants/issuer/...
```

which the gateway rewrites to IssuerService's `/api/admin/v1/participants/issuer/...`. The operator token from
chapter 03 is bound to the `issuer` context and carries `issuer-admin-api:admin`.

## Attestation definitions: where claims come from

An **attestation** produces claims about a holder at issuance time. Aurora uses the **holder attestation**, which
returns the properties stored with the holder when it was registered
([`attestation-holder.json`](../assets/payloads/issuer/attestation-holder.json)):

```json
{
  "id": "aurora-holder-attestation",
  "attestationType": "holder",
  "configuration": {}
}
```

```bash
POST /api/issuer/admin/participants/issuer/attestations
```

> **In production** the claims usually come from a system of record, such as a membership database or a
> certification registry, rather than being supplied when the holder is registered. The IssuerService ships a
> **database attestation**, which reads the columns of a table row linked to the holder's id. You can also add your
> own `AttestationSource` through an extension. See
> [Issuer Service: Extensibility](https://eclipse-edc.github.io/documentation/for-adopters/issuer-service/).
> Swapping the attestation does not change the credential definitions below, only the `attestations` they
> reference.

## Credential definitions: what gets issued

A **credential definition** ties together:
- a credential type and format;
- the attestations that supply its inputs;
- the **mappings** from attestation output to credential fields;
- optional rules;
- a validity period, in seconds.

[`credentialdef-membership.json`](../assets/payloads/issuer/credentialdef-membership.json):

```json
{
  "id": "aurora-membership-def",
  "credentialType": "MembershipCredential",
  "format": "VC1_0_JWT",
  "validity": "604800",
  "attestations": ["aurora-holder-attestation"],
  "jsonSchema": "{}",
  "jsonSchemaUrl": "https://w3id.org/aurora/credentials/membership-credential.json",
  "mappings": [
    { "input": "id",       "output": "credentialSubject.id",       "required": true },
    { "input": "memberOf", "output": "credentialSubject.memberOf", "required": true }
  ],
  "rules": []
}
```

[`credentialdef-dataprocessor.json`](../assets/payloads/issuer/credentialdef-dataprocessor.json) has the same shape.
It issues `DataProcessorCredential` and maps `level → credentialSubject.level`.

```bash
POST /api/issuer/admin/participants/issuer/credentialdefinitions
```

A few design notes:

- **`id` matters downstream.** A holder requests credentials *by credential definition id*. The CFM dataspace
  profile (chapter 06) and the manual credential request (chapter 07) both reference `aurora-membership-def` and
  `aurora-dataprocessor-def`.
- **`required: true`** makes issuance fail when an attestation does not provide the input. The request ends in
  state `ERROR`, which is better than issuing an incomplete credential.
- **`rules`** can reject issuance based on claim values, for example to issue only when `level` is one of the two
  allowed values.
- **`jsonSchemaUrl`** identifies the credential schema. It is not fetched during issuance. Publish a real schema at
  that URL so verifiers can validate the subject.
- **`additionalContext`** (not used here) adds JSON-LD contexts to the credential. A dataspace that defines its
  subject terms in a context, as Catena-X does, lists that context here and checks it in CEL with
  `withContext(...)`.

## Holders: who may ask

The IssuerService issues only to known **holders**. A holder is a DID plus properties, and with the holder
attestation those properties are the source of the claims:

```json
{
  "holderId": "did:web:identity.cpd.localhost:aurora-consumer",
  "did": "did:web:identity.cpd.localhost:aurora-consumer",
  "name": "Aurora consumer",
  "properties": { "id": "did:web:identity.cpd.localhost:aurora-consumer", "memberOf": "Aurora", "level": "processing" }
}
```

You don't create holders now. They are created **per participant during onboarding**:
- with CFM, the `registration-agent` creates the holder from the participant profile's `cfm.issuer` properties
  (chapter 06);
- manually, you `POST /participants/issuer/holders` yourself (chapter 07).

> The IssuerService also supports **anonymous** requests (`edc.issuance.anonymous.allowed=true`), where unknown
> DIDs are auto-registered. Use it only with an attestation that independently verifies the holder.

## Run it

```bash
tutorial/assets/scripts/02-issuer.sh
```

The script tolerates `409 Conflict`, so you can re-run it. It ends by reading back `aurora-membership-def`.

## Putting it together

- [ ] Attestation definition `aurora-holder-attestation`
- [ ] Credential definitions `aurora-membership-def` and `aurora-dataprocessor-def`
- [ ] You know which holder properties each definition needs (`id`, `memberOf`, `level`)

## Related

- [Issuer Service](https://eclipse-edc.github.io/documentation/for-adopters/issuer-service/)
- [Issuer Admin API (OpenAPI)](https://eclipse-edc.github.io/IdentityHub/openapi/issuer-admin-api/)
- [IdentityHub repository](https://github.com/eclipse-edc/IdentityHub) (developer docs on the issuance process and revocation)
