# Catena-X Dataspace Profile for EDC

> **Status — DRAFT.** This document targets the upcoming Catena-X *Neptune* release and is intended as a working draft,
> the profile configuration can be used as an example to configure different profiles.

## 1. Overview

The **Catena-X Neptune** dataspace profile describes how to configure an EDC connector to participate in the Catena-X
dataspace for the Neptune release. It bundles together:

- The wire protocol (Dataspace Protocol over HTTPS, version `2025-1`);
- The JSON-LD vocabulary contributed by the [Catena-X ODRL profile](https://github.com/catenax-eV/cx-odrl-profile);
- The DCP scopes needed to obtain the Verifiable Credentials Catena-X relies on;
- CEL expressions that evaluate the Catena-X ODRL operands at runtime;
- JSON Schema validators that reject malformed Catena-X policies at the management API boundary.

The profile is designed for **virtual mode** (multi-profile, multi-participant runtimes), introduced in EDC
by [DR 2026-05-11](https://github.com/eclipse-edc/Connector/tree/main/docs/developer/decision-records/2026-05-11-multi-profile-virtual-connector) on top of the
profile context concept
of [DR 2025-05-28](https://github.com/eclipse-edc/Connector/tree/main/docs/developer/decision-records/2025-05-28-dataspace-profile-context). A single EDC
runtime can therefore serve the Catena-X Neptune profile alongside other dataspaces.

### Pre-requisites

The runtime must include the EDC modules (or a BOM that pulls them in) for virtual mode.

### Reference documents

- [cx-odrl-profile](https://github.com/catenax-eV/cx-odrl-profile/) — Catena-X operands, sample policies, JSON Schemas.
- [DR 2025-05-28 Dataspace Profile Context](https://github.com/eclipse-edc/Connector/tree/main/docs/developer/decision-records/2025-05-28-dataspace-profile-context)
- [DR 2026-05-11 Multi-Profile Virtual Connector](https://github.com/eclipse-edc/Connector/tree/main/docs/developer/decision-records/2026-05-11-multi-profile-virtual-connector)
- [DR 2026-01-27 Adopt CEL Expressions](https://github.com/eclipse-edc/Connector/tree/main/docs/developer/decision-records/2026-01-27-adopt-cel-expressions)
- [DR 2026-01-30 Dynamic DCP Scopes](https://github.com/eclipse-edc/Connector/tree/main/docs/developer/decision-records/2026-01-30-dynamic-dcp-scopes)
- [DR 2025-08-01 JSON Schema Adoption](https://github.com/eclipse-edc/Connector/tree/main/docs/developer/decision-records/2025-08-01-json-schema-adoption)

## 2. Profile identity

Just as a placeholder, the profile name `cx-neptune` is used in this document identify the profile in URLs and management API negotiations.
This can be changed to a different name in the profile configuration if necessary.

| Property                                                    | Value                                                                                       | Notes                                                                                                                                                |
|-------------------------------------------------------------|---------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| Profile id                                                  | `cx-neptune`                                                                                | `TBD` — placeholder. Appears as URL segment in `/{participantContextId}/cx-neptune/...` and as the `protocol` string in management API negotiations. |
| Protocol version                                            | `2025-1`                                                                                    | DSP version handled by `dsp-2025-virtual`.                                                                                                           |
| Protocol binding                                            | `HTTPS`                                                                                     |                                                                                                                                                      |
| Protocol namespace (DSP)                                    | `https://w3id.org/dspace/2025/1/`                                                                | DSP 2025-1 default JSON-LD namespace.                                                                                                                |
| Additional JSON-LD contexts                                 | `https://w3id.org/dspace/2025/1/context.jsonld`, `<additional-context>` | `TBD` — exact CX context URL to confirm with governance. Both are loaded for compaction/expansion under this profile.                                |
| ODRL profile IRI (the `profile` field on a Catena-X policy) | `https://w3id.org/catenax/policy/cx-neptune`                                                | `TBD` — placeholder. This is the value that schema validation will key on (see §6).                                                                  |

## 3. Dataspace profile configuration

Configured via `DataspaceProfileConfigurationExtension`. Each profile is declared under `edc.dataspace.profiles.<alias>.*`; the alias is local to the runtime (it is the config
grouping key, not the profile id itself).

```properties
# Register the Catena-X Neptune profile
edc.dataspace.profiles.cxneptune.name="cx-neptune"
edc.dataspace.profiles.cxneptune.protocol.version="2025-1"
edc.dataspace.profiles.cxneptune.protocol.binding="HTTPS"
edc.dataspace.profiles.cxneptune.protocol.namespace="https://w3id.org/dspace/2025/1/"
edc.dataspace.profiles.cxneptune.jsonld.context.urls="https://w3id.org/dspace/2025/1/context.jsonld,https://w3id.org/edc/dspace/v0.0.1,https://w3id.org/catenax/2025/9/policy/context.jsonld,https://w3id.org/catenax/2025/9/policy/odrl.jsonld"
```

After this is registered, DSP endpoints exposed by `dsp-2025-virtual` controllers become reachable at:

```
POST /{participantContextId}/cx-neptune/catalog/request
POST /{participantContextId}/cx-neptune/negotiations/...
POST /{participantContextId}/cx-neptune/transfers/...
```

### Associating the profile with a participant

A profile registered at runtime level is only served for a participant once that participant is associated with it.
Association is per-participant configuration stored in the `ParticipantContextConfiguration`, keyed by 
`ParticipantProfileService.PROFILES_CONFIG_KEY`(`edc.dataspace.profiles`, comma-separated list of profile ids).

The recommended way is via the management API for getting the current associated profiles and adding/removing profiles
without restarting the runtime. The example below associates the `cx-neptune`

```http
POST /v5beta/participantcontexts/{participantContextId}/profiles
Content-Type: application/json

{
  "@context": [
    "https://w3id.org/edc/connector/management/v2"
  ],
  "@type": "AssociateDataspaceProfile",
  "profiles": ["cx-neptune"]
}
```

Participants who must always see every registered profile (test/sample runtimes) can opt out of this association step by
setting the runtime-wide `dspEnableAllProfiles` flag; production deployments SHOULD NOT do that.

For getting the current profiles associated with a participant, send a GET to the same endpoint:

```http
GET /v5beta/participantcontexts/{participantContextId}/profiles
```

will return a list of profile ids currently associated with that participant.

```json
[
  {
    "@context": [
      "https://w3id.org/edc/connector/management/v2"
    ],
    "@type": "DataspaceProfile",
    "name": "cx-neptune",
    "protocol": {
      "version": "2025-1",
      "path": "/cx-neptune",
      "binding": "HTTPS",
      "namespace": "https://w3id.org/dspace/2025/1/"
    },
    "jsonLdContextsUrl": [
      "https://w3id.org/dspace/2025/1/context.jsonld",
      "https://w3id.org/edc/dspace/v0.0.1",
      "https://w3id.org/catenax/2025/9/policy/context.jsonld",
      "https://w3id.org/catenax/2025/9/policy/odrl.jsonld"
    ]
  }
]
```

## 4. DCP scope configuration

Configured via 
`DynamicDcpScopeConfigurationExtension` under the prefix `edc.iam.dcp.scopes.<alias>.*`. Two scope categories are used:

- `DEFAULT` — requested on every DCP handshake under the profile.
- `POLICY` — requested only when a constraint whose `leftOperand` matches `prefix.mapping` appears in the policy being
  evaluated.

The `profile` setting scopes each entry to a single dataspace profile, so the same connector can serve multiple
dataspaces without leaking Catena-X scope requests to other counter-parties.

```properties
# Always-on BpnCredential
edc.iam.dcp.scopes.bpn.id=cx-bpn
edc.iam.dcp.scopes.bpn.type=DEFAULT
edc.iam.dcp.scopes.bpn.value=org.eclipse.dspace.dcp.vc.type:BpnCredential:read
edc.iam.dcp.scopes.bpn.profile=cx-neptune
# Always-on MembershipCredential
edc.iam.dcp.scopes.membership.id=cx-membership
edc.iam.dcp.scopes.membership.type=DEFAULT
edc.iam.dcp.scopes.membership.value=org.eclipse.dspace.dcp.vc.type:MembershipCredential:read
edc.iam.dcp.scopes.membership.profile=cx-neptune
# Always-on DataExchangeGovernanceCredential
edc.iam.dcp.scopes.gov.id=cx-governance
edc.iam.dcp.scopes.gov.type=DEFAULT
edc.iam.dcp.scopes.gov.value=org.eclipse.dspace.dcp.vc.type:DataExchangeGovernanceCredential:read
edc.iam.dcp.scopes.gov.profile=cx-neptune
```

> `TBD` — the literal scope values above (`org.eclipse.dspace.vc.type:MembershipCredential:read`, …) are the DCP default 
> scope grammar. Currently Catena-X uses `org.eclipse.tractusx.vc.type` which should be changed in order to achieve DCP compatibility.

## 5. CEL expressions for Catena-X ODRL operands

CEL expressions are stored in `CelExpressionStore` and made available to the policy engine by 
`CelPolicyCoreExtension` which registers a `CelExpressionFunction` against the four EDC policy scopes:

| Scope constant                                       | String value           |
|------------------------------------------------------|------------------------|
| `CatalogPolicyContext.CATALOG_SCOPE`                 | `catalog`              |
| `ContractNegotiationPolicyContext.NEGOTIATION_SCOPE` | `contract.negotiation` |
| `TransferProcessPolicyContext.TRANSFER_SCOPE`        | `transfer.process`     |
| `PolicyMonitorContext.POLICY_MONITOR_SCOPE`          | `policy.monitor`       |

In CEL expressions:

- `this` exposes the constraint under evaluation with `leftOperand`, `rightOperand` and `operator` values.
- `ctx` exposes additional information injected by components depending on the policy context (e.g. `ctx.agreement` in `transfer.process` / `policy.monitor` scopes).
- `ctx.agent` will contains the information about the `ParticipantAgent`. When using the Verifiable Credential-based handshake, `ctx.agent.vc` will contain the list of VerifiableCredential objects in the presentation mapped by `VcClaimMapper`.
- `now` is the current timestamp.

### 5.1 Expression payloads

Each expression below is the JSON-LD body to POST to the v5beta Management API:

```
POST /v5beta/celexpressions
Content-Type: application/json
```

> **Working assumption on credential shape.** The expressions below assume Catena-X credentials carry a
`credentialSubject` with the fields shown. Adopters MUST cross-check the actual issuer schema (Membership Issuance
> Service, etc..) and adjust the field paths if Catena-X uses different
> names.

#### 5.1.1 `cx-policy:Membership`

Assumed credential:

```json
{
  "type": [
    "VerifiableCredential",
    "MembershipCredential"
  ],
  "credentialSubject": {
    "holderIdentifier": "BPNL000000000001",
    "memberOf": "Catena-X"
  }
}
```

CEL expression example:

```json
{
  "@context": [
    "https://w3id.org/edc/connector/management/v2"
  ],
  "@type": "CelExpression",
  "leftOperand": "https://w3id.org/catenax/2025/9/policy/Membership",
  "description": "Catena-X membership check: requires a valid MembershipCredential.",
  "scopes": [
    "catalog",
    "contract.negotiation",
    "transfer.process"
  ],
  "expression": "ctx.agent.claims.vc.filter(c, c.type.exists(t, t == 'MembershipCredential')).exists(c, c.credentialSubject.exists(cs, cs.memberOf == 'Catena-X'))"
}
```

#### 5.1.2 `cx-policy:FrameworkAgreement`

Assumed credential (right-operand format mirrors the cx-odrl-profile example, e.g. `DataExchangeGovernance:1.0`):

> The current `FrameworkAgreement` is bound to the `DataExchangeGovernanceCredential`.

```json
{
  "type": [
    "VerifiableCredential",
    "DataExchangeGovernanceCredential"
  ],
  "credentialSubject": {
    "contractVersion": "1.0"
  }
}
```

CEL expression:

```json
{
  "@context": [
    "https://w3id.org/edc/connector/management/v2"
  ],
  "@type": "CelExpression",
  "leftOperand": "https://w3id.org/catenax/2025/9/policy/FrameworkAgreement",
  "description": "Catena-X framework agreement check: the consumer must hold a DataExchangeGovernanceCredential matching the contract version in the right operand (e.g. 1.0).",
  "scopes": [
    "catalog",
    "contract.negotiation",
    "transfer.process"
  ],
  "expression": "ctx.agent.claims.vc.filter(c, c.type.exists(t, t == 'DataExchangeGovernanceCredential')).exists(c, c.credentialSubject.exists(cs, 'DataExchangeGovernance:' + cs.contractVersion == this.rightOperand))"
}
```

#### 5.1.3 `cx-policy:BusinessPartnerNumber`


Assumed credential:

```json
{
  "type": [
    "VerifiableCredential",
    "BpnCredential"
  ],
  "credentialSubject": {
    "bpn": "BPNL000000000001"
  }
}
```

Cel expression:

```json
{
  "@context": [
    "https://w3id.org/edc/connector/management/v2"
  ],
  "@type": "CelExpression",
  "leftOperand": "https://w3id.org/catenax/2025/9/policy/BusinessPartnerNumber",
  "description": "Catena-X Business Partner Number check: the active agreement's BPN (or a configured cross-reference property) must equal the right operand.",
  "scopes": [
    "catalog",
    "contract.negotiation",
    "transfer.process"
  ],
  "expression": "ctx.agent.claims.vc.filter(c, c.type.exists(t, t == 'BpnCredential')).exists(c, c.credentialSubject.exists(cs, cs.bpn == this.rightOperand ))"
}
```

### 5.2 Bundling expressions as a seed extension (alternative)

For air-gapped or fleet-managed deployments, the same four entries can be seeded at boot by a small extension that
injects `CelExpressionStore` and calls `create()` for each `CelExpression`. This avoids the management API hop but pins
the catalogue to the runtime image; pick whichever is more appropriate for the deployment model.

## 6. JSON Schema validation for Catena-X ODRL policies

Schema validation is wired via `ManagementApiSchemaValidatorExtension`

Custom validators are declared under `edc.mgmt.api.schema.<groupAlias>.*` and may be restricted to inputs whose `policy.profile` value matches a configured list .

### 6.1 Source schemas (upstream)

Schemas are maintained in the [cx-odrl-profile repository](https://github.com/catenax-eV/cx-odrl-profile/tree/main/schema):

| Upstream file                           | Role                                                                                     |
|-----------------------------------------|------------------------------------------------------------------------------------------|
| `schema/policy-schema.json`             | Top-level ODRL Set/Offer/Agreement structure for the CX profile.                         |
| `schema/atomic-constraint-schemas.json` | Per-operand constraint shapes (e.g. allowed right operand for `cx-policy:UsagePurpose`). |
| `schema/context-schema.json`            | Expected JSON-LD `@context` shape.                                                       |


Even though the schema files are maintained in the cx-odrl-profile repository, its recommended to bundle them with your connector
or mount them from a shared volume. Otherwise the schema validator will fetch the upstream schema at runtime, which may not be available.

### 6.2 Bundling and registering

1. Copy the three schema files into the connector's classpath, e.g.
   `src/main/resources/cx-neptune/schema/policy-schema.json` etc.
2. Configure a `mapping.from` / `mapping.to` pair that redirects upstream schema URLs to the classpath. When the mapping
   is in place, the runtime resolves the schema locally and does **not** fetch the upstream URL at runtime.
3. Register a profile-scoped `PolicyDefinition` validator that activates only for inputs whose `policy.profile` equals
   the Catena-X profile IRI.

```properties
# Group declaration: target validators are registered with version prefix 'v4'
edc.mgmt.api.schema.cxneptune.version=v4
# Redirect the upstream schema prefix to the bundled classpath copies
edc.mgmt.api.schema.cxneptune.mapping.from=https://w3id.org/catenax/2025/9/policy/schema
edc.mgmt.api.schema.cxneptune.mapping.to=classpath:/cx-neptune/schema
# Validator: PolicyDefinition, applied only when policy.profile == cx-neptune
edc.mgmt.api.schema.cxneptune.validator.policy.type=PolicyDefinition
edc.mgmt.api.schema.cxneptune.validator.policy.schema=https://w3id.org/catenax/2025/9/policy/schema/policy-schema.json#/definitions/PolicyDefinition
edc.mgmt.api.schema.cxneptune.validator.policy.profiles=cx-neptune
```

### 6.3 Mounting as shared volume and registering

1. Copy the three schema files into a shared volume that can be injected into the connector runtime environment (eg. `/app/cx-neptune/schema`).
2. Configure a `mapping.from` / `mapping.to` pair that redirects upstream schema URLs to the shared volume. When the mapping
   is in place, the runtime resolves the schema locally and does **not** fetch the upstream URL at runtime.
3. Register a profile-scoped `PolicyDefinition` validator that activates only for inputs whose `policy.profile` equals
   the Catena-X profile IRI.

```properties
# Group declaration: target validators are registered with version prefix 'v4'
edc.mgmt.api.schema.cxneptune.version=v4
# Redirect the upstream schema prefix to the bundled classpath copies
edc.mgmt.api.schema.cxneptune.mapping.from=https://w3id.org/catenax/2025/9/policy/schema
edc.mgmt.api.schema.cxneptune.mapping.to=/app/cx-neptune/schema
# Validator: PolicyDefinition, applied only when policy.profile == cx-neptune
edc.mgmt.api.schema.cxneptune.validator.policy.type=PolicyDefinition
edc.mgmt.api.schema.cxneptune.validator.policy.schema=https://w3id.org/catenax/2025/9/policy/schema/policy-schema.json#/definitions/PolicyDefinition
edc.mgmt.api.schema.cxneptune.validator.policy.profiles=cx-neptune
```

> The validation is bound to the `cx-neptune` profile alias, that means that when posting a new `PolicyDefinition` the `profile` in the `Policy` object must be set to `cx-neptune`
> for the validation to be activated.

The current `https://w3id.org/catenax/2025/9/policy/schema/policy-schema.json` does not contains the definitions for `PolicyDefinition` but only `Policy`.
It should be adapted to include `PolicyDefinition` with a `policy` field that points to `https://w3id.org/catenax/2025/9/policy/schema/policy-schema.json#/definitions/CatenaXPolicy`

