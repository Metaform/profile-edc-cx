{{/*
Copyright (c) 2026 Metaform Systems, Inc.
SPDX-License-Identifier: Apache-2.0

Helpers for the cx-tck chart's templates. Naming mirrors the cx-profile / tractusx
charts (jadtx.* prefix) for consistency across the repo.
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

{{/* In-cluster FQDN builder. Usage: {{ include "jadtx.fqdn" (dict "svc" "controlplane" "ctx" $) }} */}}
{{- define "jadtx.fqdn" -}}
{{- printf "%s.%s.%s" .svc (include "jadtx.namespace" .ctx) .ctx.Values.global.clusterDomain -}}
{{- end -}}
