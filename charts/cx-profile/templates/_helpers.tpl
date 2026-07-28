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
