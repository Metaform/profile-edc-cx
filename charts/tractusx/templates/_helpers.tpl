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
