---
title: "9. Try it, troubleshoot, adapt it"
description: Run the whole tutorial in one command, fix the usual problems and turn Aurora into your own dataspace.
weight: 90
---

<!-- TOC -->
* [Run everything](#run-everything)
* [Run step by step](#run-step-by-step)
* [Explore what you built](#explore-what-you-built)
* [Troubleshooting](#troubleshooting)
* [Clean up](#clean-up)
* [Adapting Aurora to your dataspace](#adapting-aurora-to-your-dataspace)
<!-- TOC -->

## Run everything

```bash
cd tutorial/assets/scripts
./run-all.sh --mode cfm          # onboarding through CFM (chapter 06)
# or, on a fresh cluster:
./99-teardown.sh && ./run-all.sh --mode manual   # onboarding by hand (chapter 07)
```

If you already have a cluster with Traefik and the Gateway API CRDs, add `--skip-cluster`. Settings such as the host,
namespace, chart version and DID slugs come from [`env.sh`](../assets/scripts/env.sh), and every one of them can be
overridden from the environment.

A successful run ends with:

```
==> Catalog request (DSP + DCP: the provider evaluates our credentials with CEL)
    datasets visible to the consumer: ["aurora-docs-processing"]
    ✔ aurora-docs-processing offered (consumer holds DataProcessorCredential level=processing)
    ✔ aurora-docs-sensitive hidden (its access policy requires level=sensitive)
...
==> Call the provider's backend with the token
Aurora dataspace - quarterly processing report (sample document)
    ✔ data received - the dataspace works end to end
```

## Run step by step

| Script | Chapter | Idempotent |
|--------|---------|------------|
| `00-cluster.sh` | 03 | yes |
| `01-platform.sh` | 03 | yes |
| `02-issuer.sh` | 04 | yes (409 tolerated) |
| `03-connector-profile.sh` | 05 | yes (profile upserted) |
| `04-onboard-cfm.sh` *or* `04-onboard-manual.sh` | 06 / 07 | yes, skips participants already in `assets/.state/` |
| `05-backend.sh` | 08 | yes |
| `06-provider-offer.sh` | 08 | yes |
| `07-consumer-transfer.sh` | 08 | yes (new negotiation each run) |
| `99-teardown.sh` | – | deletes the cluster and `assets/.state/` |

Participant context ids are stored in `assets/.state/{provider,consumer}-pcid`. Delete that directory whenever you
recreate the platform.

## Explore what you built

```bash
source tutorial/assets/scripts/lib.sh
TOKEN=$(redline_token)
P=$(state_get provider-pcid); C=$(state_get consumer-pcid)

# DID documents
curl -s http://identity.cpd.localhost/aurora-provider/did.json | jq '.service'

# The profile and its CEL expressions
api GET "$MGMT/v5/dataspaceprofiles/aurora-2025" "$(operator_token)" | jq .
api POST "$MGMT/v5/celexpressions/request" "$(operator_token)" - <<<'{"@context":["https://w3id.org/edc/connector/management/v2"],"@type":"QuerySpec"}' | jq '.[].expression'

# The consumer's credentials
api GET "$IDENTITY/participants/$C/credentials" "$(token redline "$C" identity-api:credentials:read)" | jq .

# The provider's negotiations, as seen by the provider
api POST "$MGMT/v5/participants/$P/contractnegotiations/request" "$TOKEN" - <<<'{"@context":["https://w3id.org/edc/connector/management/v2"],"@type":"QuerySpec"}' | jq '.[] | {state, counterPartyId}'
```

Experiments worth trying:

- **Change the consumer's level to `sensitive`.**
  - CFM: delete the consumer's participant profile and deploy it again with `level: sensitive`.
  - Manual: update the holder's properties at the issuer and request the credentials again.

  The other asset then appears in the catalog.
- **Write a new rule.** Add a CEL expression for a new left operand, for example one that checks `ctx.agent.id`
  against an allow-list, and use it in a policy, without restarting anything.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `curl: (6) Could not resolve host: cpd.localhost` | Your resolver does not map `*.localhost` | Add the three hostnames to `/etc/hosts` (127.0.0.1) |
| `helm` `403 denied` on ghcr.io | Stale registry login | `helm registry logout ghcr.io` |
| `token exchange failed` | No jwtlet mapping for that SA/resource/scope | Chapter 03 (seeded mappings) or chapter 07 step 1 |
| `403` from the gateway | clearglass: the token lacks the route's scope | Decode the token (`cut -d. -f2 \| base64 -d`); compare with the CPD `security.clearglass.routeMap` |
| Credential request `ERROR` | Missing holder property for a `required` mapping, or wrong credential definition id | Check holder properties (`cfm.issuer`) and `credentialSpecs[].id`; `kubectl -n edc-v logs deploy/issuerservice` |
| CFM: `no active cell found` | CFM dataspace profile not deployed to a cell | `POST /api/tm/dataspace-profiles/{id}/deployments` |
| CFM: participant profile stays `pending` | An activity is failing or retrying | `POST /api/pm/orchestrations/query`; `kubectl -n edc-v logs deploy/cfm-agents --all-containers` |
| Catalog request fails / empty, DID resolution errors in control plane logs | did:web hostnames do not resolve **in-cluster** | Re-run `01-platform.sh` (CoreDNS rewrites); check with `getent hosts` from a debian pod |
| Catalog empty, no errors | CEL expression returned false: wrong `leftOperand` IRI, issuer not trusted for the type, claim name mismatch | `POST /v5/celexpressions/test`; compare policy `leftOperand` with the expression's |
| DSP 404 at `/api/dsp/<ctx>/<profile>` | Participant not associated with the profile, or `advertisedProfile` ≠ profile name | `GET /v5/participants/<ctx>/profiles`; check `aurora-values.yaml` |
| Transfer `TERMINATED` | No data plane for `HttpData-PULL` registered for the provider, or signaling auth failed | `kubectl -n edc-v logs deploy/siglet`; chapter 07 step 6 |
| Backend `401/403` with a token | Token not issued by Siglet, expired, or JWKS unreachable | Fetch a fresh token; check `edc.iam.siglet.*` in `docshare-config` |
| docshare `ImagePullBackOff` | Image not published for your architecture | Build it from [JAD](https://github.com/eclipse-dataspace-hub/jad) and `kind load docker-image` it |

## Clean up

```bash
tutorial/assets/scripts/99-teardown.sh
```

## Adapting Aurora to your dataspace

1. **Profile.** Copy [`payloads/`](../assets/payloads) and rename `aurora-2025`, the vocabulary IRI (`AURORA_VOCAB`),
   the credential types and the definition ids. Keep the ids consistent across the issuer definitions, the CFM
   `credentialSpecs` and the credential requests.
2. **Credentials.** Replace the holder attestation with an attestation over your system of record. Add `rules` and
   real JSON schemas.
3. **Vocabulary.** Publish a JSON-LD context for your left operands and cache it (chapter 05, optional section).
4. **Platform.** On a real cluster:
   - set `global.host` to a resolvable domain;
   - serve the DID hosts over HTTPS (`global.external.scheme`);
   - drop the CoreDNS rewrites and `allowHttp`;
   - replace Vault dev mode and the in-chart Postgres.
5. **Onboarding.**
   - With CFM, add role-specific `credentialSpecs` (`role`) and per-participant `vpaProperties`.
   - Without CFM, turn chapter 07 into your own provisioning workflow.
6. **Backends.** Validate Siglet tokens in your services and use `claimMappings` to pass the credential facts they
   need.

The [Catena-X profile](../../README.md) in this repository is a production-scale example of the same structure.
