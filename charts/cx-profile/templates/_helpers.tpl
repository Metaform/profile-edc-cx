{{/*
Copyright (c) 2025 Metaform Systems, Inc.
SPDX-License-Identifier: Apache-2.0

Helpers for the jad-tractusx wrapper chart's own templates (e.g. HTTPRoutes).
Naming mirrors the jad-dataspace-profile chart for consistency across the repo.
*/}}

{{- define "jadtx.namespace" -}}
{{- .Values.global.namespace | default .Release.Namespace -}}
{{- end -}}

{{- define "jadtx.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "jadtx.labels" -}}
helm.sh/chart: {{ include "jadtx.chart" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/instance: {{ .Release.Name }}
platform: edcv
{{- end -}}



{{/* In-cluster FQDN builder. Usage: {{ include "jadtx.fqdn" (dict "svc" "issuerservice" "ctx" $) }} */}}
{{- define "jadtx.fqdn" -}}
{{- printf "%s.%s.%s" .svc (include "jadtx.namespace" .ctx) .ctx.Values.global.clusterDomain -}}
{{- end -}}


{{/* -------------------------------------------------------------------------
     Image reference.
     Usage: {{ include "jadtx.image" (dict "img" .Values.edc.dataplane.image "ctx" $) }}
     A repo that already contains a registry host (has a dot or colon before the
     first slash) is used verbatim; otherwise imageRegistry is prepended.
     ------------------------------------------------------------------------- */}}
{{- define "jadtx.image" -}}
{{- $repo := .img.repo -}}
{{- $tag := .img.tag | default "latest" -}}
{{- $first := splitList "/" $repo | first -}}
{{- if or (contains "." $first) (contains ":" $first) -}}
{{- printf "%s:%s" $repo $tag -}}
{{- else -}}
{{- printf "%s/%s:%s" .ctx.Values.imageRegistry $repo $tag -}}
{{- end -}}
{{- end -}}

{{/* Effective imagePullPolicy: per-component override when set, else global default. */}}
{{- define "jadtx.pullPolicy" -}}
{{- .policy | default .ctx.Values.global.imagePullPolicy -}}
{{- end -}}

{{/* -------------------------------------------------------------------------
     envFrom for a standard app: its own config ConfigMap + telemetry-config.
     telemetry-config is provisioned by the platform chart in the same namespace.
     Usage: {{ include "jadtx.appEnvFrom" (dict "config" "dataplane-config" "ctx" $) | nindent 12 }}
     ------------------------------------------------------------------------- */}}
{{- define "jadtx.appEnvFrom" -}}
- configMapRef:
    name: {{ .config }}
{{- if .ctx.Values.telemetry.configMapEnabled }}
- configMapRef:
    name: telemetry-config
{{- end -}}
{{- end -}}

{{/* -------------------------------------------------------------------------
     Infra service hosts. `connection.host` wins; otherwise derive the
     community sub-chart service name (<release>-postgresql / -vault / -nats).
     NOTE: this chart is a separate release from the platform, so
     `connection.host` MUST be set explicitly in values.yaml to point at the
     platform release's services rather than derived from this release's name.
     ------------------------------------------------------------------------- */}}
{{- define "jadtx.pgHost" -}}
{{- $c := .Values.postgresql.connection -}}
{{- if $c.host -}}{{ $c.host -}}
{{- else -}}{{ printf "%s-postgresql.%s.%s" .Release.Name (include "jadtx.namespace" .) .Values.global.clusterDomain -}}{{- end -}}
{{- end -}}

{{- define "jadtx.vaultHost" -}}
{{- $c := .Values.vault.connection -}}
{{- if $c.host -}}{{ $c.host -}}
{{- else -}}{{ printf "%s-vault.%s.%s" .Release.Name (include "jadtx.namespace" .) .Values.global.clusterDomain -}}{{- end -}}
{{- end -}}

{{- define "jadtx.natsHost" -}}
{{- $c := .Values.nats.connection -}}
{{- if $c.host -}}{{ $c.host -}}
{{- else -}}{{ printf "%s-nats.%s.%s" .Release.Name (include "jadtx.namespace" .) .Values.global.clusterDomain -}}{{- end -}}
{{- end -}}

{{/* Convenience URL builders reused across configs. */}}
{{- define "jadtx.vaultUrl" -}}
{{- printf "%s://%s:%v" .Values.vault.connection.scheme (include "jadtx.vaultHost" .) .Values.vault.connection.port -}}
{{- end -}}

{{- define "jadtx.natsUrl" -}}
{{- printf "nats://%s:%v" (include "jadtx.natsHost" .) .Values.nats.connection.port -}}
{{- end -}}

{{/* Projected jwtlet subject-token volume (RFC 8693 subject_token). */}}
{{- define "jadtx.jwtletSubjectTokenVolume" -}}
- name: jwtlet-subject-token
  projected:
    sources:
      - serviceAccountToken:
          path: token
          audience: {{ .Values.global.jwtSubjectTokenAudience | quote }}
          expirationSeconds: {{ .Values.global.jwtSubjectTokenExpirationSeconds }}
{{- end -}}


{{/* Trusted-issuer DID, taken verbatim from `issuer.did`.

     It MUST equal the DID the platform actually minted for the issuer participant context: it is
     written into the dataspace profile's credentialSpecs, and every issued credential is verified
     against it. A mismatch surfaces only at credential verification during onboarding, far from
     the cause, so this is deliberately a plain configured value rather than something derived —
     core-platform-distribution builds the DID from its own `global.external.*` settings, and any
     second copy of that rule silently drifts when the platform is reconfigured.

     Read the live value off the deployment with either of:
       kubectl -n <ns> get httproute issuerservice-did -o jsonpath='{.spec.hostnames[0]}'
       helm -n <ns> get values core-platform */}}
{{- define "jadtx.issuerDid" -}}
{{- required "issuer.did must be set to the platform's issuer DID (see values.yaml)" .Values.issuer.did -}}
{{- end -}}


{{/* trustedIssuers array for the dataspace profile, rendered as inline JSON.

     `issuer.did` is always the first entry; anything in `issuer.trustedIssuers` is appended to
     it, so the local issuer can never be accidentally dropped. Duplicates are removed (re-listing
     `issuer.did` there is harmless). Every entry trusts all credential types
     ("supportedTypes": ["*"]). */}}
{{- define "jadtx.trustedIssuers" -}}
{{- $dids := concat (list (include "jadtx.issuerDid" .)) (.Values.issuer.trustedIssuers | default (list)) | uniq -}}
{{- $out := list -}}
{{- range $dids -}}
{{- $out = append $out (dict "@id" . "supportedTypes" (list "*")) -}}
{{- end -}}
{{- toJson $out -}}
{{- end -}}
