{{/* Chart name, overridable. */}}
{{- define "patrimoine.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Fully qualified name, used as the base of every resource name. Kubernetes names are limited to
63 characters, hence the truncation.
*/}}
{{- define "patrimoine.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- define "patrimoine.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/* Labels every resource carries. */}}
{{- define "patrimoine.labels" -}}
helm.sh/chart: {{ include "patrimoine.chart" . }}
{{ include "patrimoine.baseSelectorLabels" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: patrimoine
{{- end }}

{{/*
Selector labels shared by every component of a release. Each component adds its own
app.kubernetes.io/component, so the API's Service can never select the demo database's pod.
Selectors are immutable once a Deployment exists: nothing volatile, such as a version, goes here.
*/}}
{{- define "patrimoine.baseSelectorLabels" -}}
app.kubernetes.io/name: {{ include "patrimoine.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "patrimoine.selectorLabels" -}}
{{ include "patrimoine.baseSelectorLabels" . }}
app.kubernetes.io/component: api
{{- end }}

{{- define "patrimoine.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "patrimoine.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/* A digest wins over a tag; the tag defaults to the chart's appVersion. */}}
{{- define "patrimoine.image" -}}
{{- if .Values.image.digest }}
{{- printf "%s@%s" .Values.image.repository .Values.image.digest }}
{{- else }}
{{- printf "%s:%s" .Values.image.repository (.Values.image.tag | default .Chart.AppVersion) }}
{{- end }}
{{- end }}

{{- define "patrimoine.webImage" -}}
{{- if .Values.web.image.digest }}
{{- printf "%s@%s" .Values.web.image.repository .Values.web.image.digest }}
{{- else }}
{{- printf "%s:%s" .Values.web.image.repository (.Values.web.image.tag | default .Chart.AppVersion) }}
{{- end }}
{{- end }}

{{- define "patrimoine.databaseSecret" -}}
{{- .Values.database.existingSecret | default (printf "%s-database" (include "patrimoine.fullname" .)) }}
{{- end }}

{{/* Fails the install with a readable message rather than deploying a pod that cannot start. */}}
{{- define "patrimoine.databaseUrl" -}}
{{- if .Values.demo.postgresql.enabled }}
{{- printf "jdbc:postgresql://%s-postgresql:5432/%s" (include "patrimoine.fullname" .) .Values.demo.postgresql.database }}
{{- else }}
{{- required "database.url is required unless demo.postgresql.enabled is true" .Values.database.url }}
{{- end }}
{{- end }}

{{- define "patrimoine.redisHost" -}}
{{- if .Values.demo.redis.enabled }}
{{- printf "%s-redis" (include "patrimoine.fullname" .) }}
{{- else }}
{{- .Values.redis.host }}
{{- end }}
{{- end }}

{{- define "patrimoine.springProfiles" -}}
{{- $profiles := .Values.springProfiles }}
{{- if .Values.demo.seedData }}
{{- $profiles = append $profiles "demo" }}
{{- end }}
{{- join "," $profiles }}
{{- end }}
