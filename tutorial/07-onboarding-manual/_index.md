---
title: "7. Onboarding manually"
description: Onboard a participant without an orchestrator by calling each platform API yourself.
weight: 70
---

<!-- TOC -->
* [When to do this](#when-to-do-this)
* [Before you start](#before-you-start)
* [Step 1: token bindings (jwtlet)](#step-1-token-bindings-jwtlet)
* [Step 2: identity (IdentityHub)](#step-2-identity-identityhub)
* [Step 3: connector (control plane)](#step-3-connector-control-plane)
* [Step 4: holder (IssuerService)](#step-4-holder-issuerservice)
* [Step 5: credentials (DCP issuance)](#step-5-credentials-dcp-issuance)
* [Step 6: data plane (Siglet)](#step-6-data-plane-siglet)
* [What you don't do: key distribution](#what-you-dont-do-key-distribution)
* [Comparison with CFM](#comparison-with-cfm)
* [Putting it together](#putting-it-together)
<!-- TOC -->

Script: [`04-onboard-manual.sh`](../assets/scripts/04-onboard-manual.sh). Payloads:
[`payloads/manual/`](../assets/payloads/manual).

## When to do this

- **To understand** what an onboarding actually involves. Each step below corresponds to one CFM agent.
- **To integrate** EDC components into an orchestration tool of your own, such as a portal backend, Terraform or
  Crossplane, instead of CFM.
- **To debug** a failing CFM activity by repeating its calls.

> Run the manual path on a fresh platform, or with different `PROVIDER_SLUG`/`CONSUMER_SLUG`. Do not combine it with
> chapter 06 for the same DIDs.

## Before you start

**You choose the participant context id.** CFM generates one, but here any URL-safe string works. The tutorial
uses the DID slug, so the provider's id is `aurora-provider`.

**Two of the APIs are not on the gateway.**

| API | Why it's internal | How the script reaches it |
|-----|-------------------|---------------------------|
| jwtlet management (`jwtlet:8081/api/v1`) | Decides who may obtain tokens for which participant. Only the `cfm-agents` and `seed-jobs` ServiceAccounts are authorized | `kubectl port-forward svc/jwtlet 18081:8081`, bearer = raw `seed-jobs` SA token |
| Siglet management (`siglet:8083`) | Per-participant backend endpoints | `kubectl port-forward svc/siglet 18083:8083`, participant token with `siglet-mgmt-api:write` |

Everything else goes through `http://cpd.localhost` as in the previous chapters.

## Step 1: token bindings (jwtlet)

> Mirrors the CFM **jwtlet-agent**.

A participant context exists for the platform only once jwtlet knows who may act for it. You create four
**resource mappings**:

| Client (ServiceAccount) | Scopes | Purpose |
|-------------------------|--------|---------|
| `redline` (you, the operator) | `management-api:admin identity-api:admin identity-api:credentials:read/write issuer-admin-api:admin siglet-mgmt-api:read/write read write` | Steps 2–6 below |
| `controlplane` | `read signaling` | Read the participant's Vault folder; authorize DPS signaling to Siglet |
| `identityhub` | `read` | Read the participant's Vault folder (its private key) |
| `siglet-sa` | `read` | Read the participant's Vault folder |

[`jwtlet-mapping-operator.json`](../assets/payloads/manual/jwtlet-mapping-operator.json):

```json
{
  "clientIdentifier": "system:serviceaccount:edc-v:redline",
  "participantContext": "aurora-provider",
  "scopes": ["management-api:admin", "identity-api:admin", "identity-api:credentials:read", "identity-api:credentials:write",
             "issuer-admin-api:admin", "siglet-mgmt-api:read", "siglet-mgmt-api:write", "read", "write"],
  "audiences": ["edcv"]
}
```

```bash
POST http://localhost:18081/api/v1/mappings        # x4, bearer: kubectl create token seed-jobs ...
```

From now on, calls use a **participant-bound token**:

```bash
token redline aurora-provider "management-api:admin identity-api:admin ... read write"
```

Here `resource=aurora-provider`. The workload mappings matter even though you never use them yourself. Without
them, the control plane, IdentityHub and Siglet cannot open the participant's Vault partition, and they fail later
with authorization errors that are hard to trace.

## Step 2: identity (IdentityHub)

> Mirrors the CFM **ih-agent**.

[`ih-participant.json`](../assets/payloads/manual/ih-participant.json) creates the participant context, its DID
document and its first key pair in one call:

```json
{
  "participantContextId": "aurora-provider",
  "did": "did:web:identity.cpd.localhost:aurora-provider",
  "active": true,
  "roles": ["participant"],
  "serviceEndpoints": [
    { "type": "CredentialService", "id": "aurora-provider-credentialservice", "serviceEndpoint": "http://cpd.localhost/api/credentials/v1/participants/aurora-provider" },
    { "type": "ProtocolEndpoint",  "id": "aurora-provider-dsp",               "serviceEndpoint": "http://cpd.localhost/api/dsp/aurora-provider/aurora-2025" },
    { "type": "DataService",       "id": "aurora-provider-dataservice",       "serviceEndpoint": "http://cpd.localhost/api/dsp/aurora-provider/.well-known/dspace-version" }
  ],
  "key": {
    "keyId": "did:web:identity.cpd.localhost:aurora-provider#key1",
    "privateKeyAlias": "aurora-provider-key1",
    "keyGeneratorParams": { "algorithm": "EDDSA", "curve": "Ed25519" }
  },
  "additionalProperties": {
    "edc.vault.hashicorp.config": {
      "config": { "vaultUrl": "http://core-platform-vault.edc-v.svc.cluster.local:8200",
                  "secretPath": "v1/participants", "folderPath": "aurora-provider/identityhub" }
    }
  }
}
```

```bash
POST http://cpd.localhost/api/identity/participants
curl http://identity.cpd.localhost/aurora-provider/did.json      # the DID now resolves
```

- **`CredentialService`** is where counterparties send DCP presentation queries.
- **`ProtocolEndpoint`** is how counterparties discover the participant's DSP endpoint. Its last segment is the
  profile name.
- **`edc.vault.hashicorp.config`** confines the participant's secrets to its own Vault folder. Access to that folder
  is authorized through the jwtlet mappings from step 1.

## Step 3: connector (control plane)

> Mirrors the CFM **edcv-agent**.

```bash
POST  http://cpd.localhost/api/management/v5/participants                       # cp-participant.json
PATCH http://cpd.localhost/api/management/v5/participants/aurora-provider/config  # cp-config.json
PUT   http://cpd.localhost/api/management/v5/participants/aurora-provider/profiles # cp-profiles.json
```

- [`cp-participant.json`](../assets/payloads/manual/cp-participant.json) creates a `ParticipantContext` with
  `"identity": "<DID>"` and `"state": "ACTIVATED"`.
- [`cp-config.json`](../assets/payloads/manual/cp-config.json) holds per-participant runtime configuration. It sets
  `edc.participant.id` (the DID the connector presents on DSP) and, as a *private* entry, the participant's Vault
  folder `aurora-provider/controlplane`.
- [`cp-profiles.json`](../assets/payloads/manual/cp-profiles.json) associates the participant with `aurora-2025`.
  From now on `/api/dsp/aurora-provider/aurora-2025` is served:

  ```json
  { "@context": ["https://w3id.org/edc/connector/management/v2"], "@type": "AssociateDataspaceProfile", "profiles": ["aurora-2025"] }
  ```

## Step 4: holder (IssuerService)

> Mirrors the CFM **registration-agent**.

The issuer must know the participant before it issues anything. The holder's properties are the claims the holder
attestation (chapter 04) will return ([`issuer-holder.json`](../assets/payloads/manual/issuer-holder.json)):

```json
{
  "holderId": "did:web:identity.cpd.localhost:aurora-provider",
  "did": "did:web:identity.cpd.localhost:aurora-provider",
  "name": "Aurora provider",
  "properties": { "id": "did:web:identity.cpd.localhost:aurora-provider", "memberOf": "Aurora", "level": "sensitive" }
}
```

```bash
POST http://cpd.localhost/api/issuer/admin/participants/issuer/holders       # operator token (issuer context)
```

In a real dataspace this is the **governance** step: the operator decides that an organization is admitted and
at which level. Everything else is technical plumbing.

## Step 5: credentials (DCP issuance)

> Mirrors the CFM **onboarding-agent**.

Ask the participant's IdentityHub to request credentials from the issuer
([`credential-request.json`](../assets/payloads/manual/credential-request.json)):

```json
{
  "issuerDid": "did:web:issuer.cpd.localhost:issuer",
  "credentials": [
    { "id": "aurora-membership-def",    "type": "MembershipCredential",    "format": "VC1_0_JWT" },
    { "id": "aurora-dataprocessor-def", "type": "DataProcessorCredential", "format": "VC1_0_JWT" }
  ]
}
```

```bash
POST http://cpd.localhost/api/identity/participants/aurora-provider/credentials/request
# 201 Created, Location: .../credentials/request/<holderPid>
GET  http://cpd.localhost/api/identity/participants/aurora-provider/credentials/request/<holderPid>
# {"status": "CREATED"} ... {"status": "ISSUED"}
```

Behind the scenes:
1. IdentityHub resolves the issuer DID and finds its `IssuerService` endpoint.
2. It sends a DCP `CredentialRequestMessage`, signed with the participant's key.
3. The IssuerService authenticates the holder, runs the holder attestation and maps `memberOf` and `level` into the
   credential subjects.
4. It signs the credentials and delivers them to the participant's storage endpoint.

`ERROR` usually means a required mapping input is missing (check the holder properties) or the definition id is
wrong.

## Step 6: data plane (Siglet)

> Mirrors the CFM **siglet-agent**.

Tell Siglet where this participant's backend lives
([`siglet-transfer-type-mapping.json`](../assets/payloads/manual/siglet-transfer-type-mapping.json)), then
register Siglet as the participant's data plane in the control plane
([`cp-dataplane.json`](../assets/payloads/manual/cp-dataplane.json)):

```bash
POST http://localhost:18083/transfer-type-mappings                                   # or PUT .../{ctx} if it exists
PUT  http://cpd.localhost/api/management/v5/participants/aurora-provider/dataplanes
```

```json
{
  "dataplaneId": "aurora-provider-siglet",
  "transferTypes": ["HttpData-PULL"],
  "endpoint": "http://siglet.edc-v.svc.cluster.local:8081/api/v1/aurora-provider/dataflows",
  "authorization": {
    "type": "oauth2_token_exchange",
    "tokenExchangeEndpoint": "http://jwtlet.edc-v.svc.cluster.local:8080",
    "issuer": "http://jwtlet.edc-v.svc.cluster.local:8080",
    "jwksUri": "http://jwtlet.edc-v.svc.cluster.local:8080/.well-known/jwks.json",
    "resource": "aurora-provider",
    "scope": "signaling"
  }
}
```

The `authorization` block is the DPS **authorization profile**. To signal Siglet, the control plane exchanges its
ServiceAccount token at jwtlet for a token bound to `aurora-provider` with scope `signaling`. That exchange works
because of the `controlplane` mapping from step 1. Siglet validates the token against jwtlet's JWKS.

## What you don't do: key distribution

When IdentityHub activated the key pair in step 2, it emitted a `keypair.activated` event on NATS. The CFM
**key-management lifecycle agent** runs whether or not you use CFM for onboarding. It consumed the event and
published the key to Siglet's key mappings and to the control plane's STS signing configuration.

Without CFM at all, you would subscribe to these events yourself, or call those two APIs after each key rotation.

## Comparison with CFM

| | CFM (chapter 06) | Manual (this chapter) |
|-|------------------|-----------------------|
| Calls you make | 1 per participant (`POST participant-profiles`) | ≈ 12 per participant |
| Participant context id | generated | chosen |
| Ordering, retries, rollback | orchestrated, with compensation on failure | your responsibility |
| Offboarding | `DELETE participant-profile` | reverse every step yourself |
| Internal APIs (jwtlet, Siglet mgmt) | called by agents in-cluster | port-forward or in-cluster job |

## Putting it together

For each participant:

- [ ] jwtlet mappings for the operator and the three workloads
- [ ] IdentityHub participant context; `did.json` resolves
- [ ] control plane participant context, config, profile association
- [ ] holder at the issuer with `memberOf` and `level`
- [ ] credential request `ISSUED`
- [ ] Siglet transfer-type mapping; data plane registered
