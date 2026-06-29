# Migrate from Hand-Crafted Helm Chart to Quarkus Helm Extension

**Branch**: `feat/quarkus-helm-migration`  
**Reference implementation**: `eks-d-xpress-control-plane` (same stack: Quarkus + JOSDK, same org)

---

## Why Migrate

The current `charts/kube-microvm-operator/` is a hand-crafted chart maintained separately from
the operator code. This causes several problems:

| Problem | Impact |
|---------|--------|
| Image tag pinned in `values.yaml` as `latest` — requires `yq` sed-hacks in CI to set release version | Fragile, easy to forget |
| Chart templates duplicate what Quarkus Kubernetes extension already generates (Deployment, Service, ServiceAccount, RBAC, health probes, resource limits) | Double maintenance |
| Adding a new env var or config property requires updating both `application.properties` AND `values.yaml` manually | Error-prone drift |
| CRD files in `charts/kube-microvm-operator/crds/` are hand-maintained, can go out of sync with model classes | Silent breakage |

With Quarkus Helm extension (`io.quarkiverse.helm:quarkus-helm`):
- Deployment, Service, ServiceAccount, RBAC, probes, resources → **auto-generated** from
  `application.properties` + `quarkus.kubernetes.*` properties
- Image tag → set at build time via `-Dquarkus.container-image.tag=${VERSION}`, no post-processing
- Custom resources (CRDs, MutatingWebhookConfiguration, ClusterRole for tokens) → declared in
  `src/main/kubernetes/kubernetes.yml`, merged into chart at build time
- `values.yaml` overrides → placed in `src/main/helm/values.yaml`, merged at build time

---

## How It Works (eks-d-xpress pattern)

```
src/main/resources/application.properties
  quarkus.helm.name=kube-microvm-operator
  quarkus.helm.create-tar-file=true
  quarkus.container-image.registry=ghcr.io
  quarkus.container-image.group=plasticity-of-cloud
  quarkus.container-image.name=kube-microvm-operator
  quarkus.container-image.tag=latest   ← overridden at build: -Dquarkus.container-image.tag=0.0.1
  quarkus.kubernetes.service-type=ClusterIP
  quarkus.kubernetes.namespace=kube-microvm
  quarkus.kubernetes.resources.requests.cpu=100m
  ... etc

src/main/kubernetes/kubernetes.yml    ← custom k8s objects merged into chart
  ClusterRole (RBAC for operator)
  ClusterRole (microvm-token-requester)
  MutatingWebhookConfiguration
  ValidatingWebhookConfiguration
  CRDs (microvms, microvmimages, ...)

src/main/helm/values.yaml             ← only the overrides that need to be user-configurable
  {}  (or specific overrides)

↓ mvn package -Dquarkus.helm.version=${VERSION} -Dquarkus.container-image.tag=${VERSION}

target/helm/kubernetes/kube-microvm-operator/
  Chart.yaml          ← version + appVersion from build properties
  values.yaml         ← generated: image, probes, resources, env vars
  values.schema.json  ← generated
  templates/
    deployment.yaml   ← generated
    service.yaml      ← generated
    serviceaccount.yaml ← generated
    clusterrole.yaml  ← from kubernetes.yml
    clusterrolebinding.yaml
    mutatingwebhookcfg.yaml
    validatingwebhookcfg.yaml
    crds/             ← from kubernetes.yml
```

---

## Migration Steps

### Step 1: Add `quarkus-helm` dependency to `operator-controller/pom.xml`

```xml
<dependency>
    <groupId>io.quarkiverse.helm</groupId>
    <artifactId>quarkus-helm</artifactId>
</dependency>
```

Add to parent `pom.xml` dependencyManagement (get version from Quarkiverse BOM):

```xml
<dependency>
    <groupId>io.quarkiverse.helm</groupId>
    <artifactId>quarkus-helm</artifactId>
    <version>${quarkus-helm.version}</version>
</dependency>
```

Check current Quarkiverse Helm version compatible with Quarkus 3.36.x:
```bash
mvn dependency:get -Dartifact=io.quarkiverse.helm:quarkus-helm:LATEST
```

### Step 2: Configure `operator-controller/src/main/resources/application.properties`

Add Quarkus Helm + Kubernetes + container-image configuration:

```properties
# ─── Helm chart generation ────────────────────────────────────────────────
quarkus.helm.name=kube-microvm-operator
quarkus.helm.description=Kubernetes operator for AWS Lambda MicroVMs
quarkus.helm.create-tar-file=true
quarkus.helm.notes=templates/NOTES.txt
quarkus.helm.version=0.0.1-SNAPSHOT

# ─── Container image ──────────────────────────────────────────────────────
quarkus.container-image.registry=ghcr.io
quarkus.container-image.group=plasticity-of-cloud
quarkus.container-image.name=kube-microvm-operator
quarkus.container-image.tag=latest

# ─── Kubernetes deployment ────────────────────────────────────────────────
quarkus.kubernetes.namespace=kube-microvm
quarkus.kubernetes.service-type=ClusterIP
quarkus.kubernetes.service-account=kube-microvm-operator
quarkus.kubernetes.replicas=1

# Resources
quarkus.kubernetes.resources.requests.cpu=100m
quarkus.kubernetes.resources.requests.memory=256Mi
quarkus.kubernetes.resources.limits.cpu=500m
quarkus.kubernetes.resources.limits.memory=512Mi

# Env vars (user-overridable via values.yaml)
quarkus.kubernetes.env.vars.microvm.aws.region=us-east-1

# Health probes — map to Quarkus SmallRye health endpoints
quarkus.kubernetes.liveness-probe.http-action-path=/q/health/live
quarkus.kubernetes.readiness-probe.http-action-path=/q/health/ready

# Ports
quarkus.kubernetes.ports.http.container-port=8080
quarkus.kubernetes.ports.https.container-port=8443
```

### Step 3: Create `operator-controller/src/main/kubernetes/kubernetes.yml`

This file is merged into the generated chart at build time. It contains all resources that
Quarkus Kubernetes extension cannot auto-generate:

```yaml
# ─── Operator ClusterRole ─────────────────────────────────────────────────
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: kube-microvm-operator
rules:
- apiGroups: ["lambda.aws.amazon.com"]
  resources: ["microvms", "microvmimages", "microvmnetworks", "microvmclasses",
              "microvmreplicasets", "microvmtemplates"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]
- apiGroups: ["lambda.aws.amazon.com"]
  resources: ["microvms/status", "microvmimages/status", "microvmnetworks/status",
              "microvmreplicasets/status"]
  verbs: ["get", "update", "patch"]
- apiGroups: ["lambda.aws.amazon.com"]
  resources: ["microvms/finalizers", "microvmimages/finalizers", "microvmnetworks/finalizers"]
  verbs: ["update"]
- apiGroups: ["authentication.k8s.io"]
  resources: ["tokenreviews"]
  verbs: ["create"]
- apiGroups: ["authorization.k8s.io"]
  resources: ["subjectaccessreviews"]
  verbs: ["create"]
- apiGroups: [""]
  resources: ["events", "serviceaccounts", "pods"]
  verbs: ["get", "list", "watch", "create", "patch"]
- apiGroups: ["apiextensions.k8s.io"]
  resources: ["customresourcedefinitions"]
  verbs: ["get", "list", "watch"]
- apiGroups: ["admissionregistration.k8s.io"]
  resources: ["mutatingwebhookconfigurations", "validatingwebhookconfigurations"]
  verbs: ["get", "list", "watch"]
---
# ─── Token requester ClusterRole (for pods that need VM tokens) ───────────
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: microvm-token-requester
rules:
- apiGroups: ["lambda.aws.amazon.com"]
  resources: ["microvms/token"]
  verbs: ["create"]
---
# ─── MutatingWebhookConfiguration ────────────────────────────────────────
apiVersion: admissionregistration.k8s.io/v1
kind: MutatingWebhookConfiguration
metadata:
  name: kube-microvm-operator-mutating
  annotations:
    cert-manager.io/inject-ca-from: "kube-microvm/kube-microvm-operator-webhook-cert"
webhooks:
- name: mutate.microvms.lambda.aws.amazon.com
  admissionReviewVersions: ["v1"]
  sideEffects: None
  failurePolicy: Ignore
  clientConfig:
    service:
      name: kube-microvm-operator
      namespace: kube-microvm
      path: /mutate-microvm
      port: 443
  rules:
  - apiGroups: ["lambda.aws.amazon.com"]
    apiVersions: ["v1alpha1"]
    operations: ["CREATE", "UPDATE"]
    resources: ["microvms"]
- name: pod-inject.lambda.aws.amazon.com
  admissionReviewVersions: ["v1"]
  sideEffects: None
  failurePolicy: Ignore
  clientConfig:
    service:
      name: kube-microvm-operator
      namespace: kube-microvm
      path: /mutate-pod
      port: 443
  rules:
  - apiGroups: [""]
    apiVersions: ["v1"]
    operations: ["CREATE"]
    resources: ["pods"]
  namespaceSelector:
    matchLabels:
      lambda.microvm.auth/inject: "enabled"
---
# ─── ValidatingWebhookConfiguration ──────────────────────────────────────
apiVersion: admissionregistration.k8s.io/v1
kind: ValidatingWebhookConfiguration
metadata:
  name: kube-microvm-operator-validating
  annotations:
    cert-manager.io/inject-ca-from: "kube-microvm/kube-microvm-operator-webhook-cert"
webhooks:
- name: validate.microvms.lambda.aws.amazon.com
  admissionReviewVersions: ["v1"]
  sideEffects: None
  failurePolicy: Fail
  clientConfig:
    service:
      name: kube-microvm-operator
      namespace: kube-microvm
      path: /validate-microvm
      port: 443
  rules:
  - apiGroups: ["lambda.aws.amazon.com"]
    apiVersions: ["v1alpha1"]
    operations: ["CREATE", "UPDATE"]
    resources: ["microvms", "microvmimages", "microvmnetworks"]
---
# ─── CRDs ────────────────────────────────────────────────────────────────
# CRDs are included via quarkus.helm.values[0].property pointing to charts/kube-microvm-operator/crds/
# OR copied into src/main/kubernetes/ — see notes below
```

> **Note on CRDs**: Quarkus JOSDK auto-generates CRD YAML files into `target/kubernetes/*.yaml`
> when `quarkus.operator-sdk.generate-csv=true` or when the model classes carry `@Group/@Version/@Kind`
> annotations (which they do). The CRD files can be included in the Helm chart by setting
> `quarkus.helm.values-schema.enabled=true` or by referencing them from `kubernetes.yml`.
> The simplest approach: let JOSDK generate CRDs and copy them into the chart's `crds/` directory
> as part of the Maven build (`maven-resources-plugin`), replacing the current hand-maintained files.

### Step 4: Create `operator-controller/src/main/helm/values.yaml`

Only configurable values that operators need to override — not the full default set:

```yaml
# User-overridable values for kube-microvm-operator
# Full defaults are in the generated values.yaml

# AWS configuration (required)
app:
  envs:
    microvm.aws.region: ""   # Set this: us-east-1, us-east-2, etc.

# IRSA role ARN (if using IRSA instead of Pod Identity)
serviceAccount:
  annotations: {}
  # example for IRSA:
  # eks.amazonaws.com/role-arn: arn:aws:iam::ACCOUNT:role/ROLE

# Token injection sidecar
authAgent:
  image:
    repository: ghcr.io/plasticity-of-cloud/microvm-auth-agent
    tag: latest

tokenEndpoint:
  enabled: true
  maxExpiryMinutes: 60
```

### Step 5: Update CI workflow — `helm-chart` job

Replace the current `sed`+`yq` approach with Quarkus Helm build:

```yaml
- name: Build and push Helm chart to GHCR (OCI)
  run: |
    VERSION=${GITHUB_REF_NAME#v}
    OWNER=$(echo "${{ github.repository_owner }}" | tr '[:upper:]' '[:lower:]')
    REGISTRY=ghcr.io/${OWNER}

    mvn -B -pl operator-controller package -DskipTests \
      -Dquarkus.helm.version=${VERSION} \
      -Dquarkus.container-image.tag=${VERSION} \
      -Dquarkus.container-image.build=false   # don't build Docker image here (done separately)

    helm push operator-controller/target/helm/kubernetes/kube-microvm-operator-${VERSION}.tar.gz \
      oci://${REGISTRY}/helm
```

The `-Dquarkus.container-image.tag=${VERSION}` sets the image tag in the generated `values.yaml`
automatically — no `yq` or `sed` needed.

### Step 6: Delete the hand-crafted chart

```bash
git rm -r charts/kube-microvm-operator/
```

---

## What Quarkus Helm Generates vs What We Keep Custom

| Resource | Generated by Quarkus | Custom (kubernetes.yml) |
|----------|---------------------|-------------------------|
| Deployment | ✅ (from app.properties) | — |
| Service | ✅ | — |
| ServiceAccount | ✅ | — |
| ConfigMap (env vars) | ✅ | — |
| Health probes | ✅ | — |
| Resource limits | ✅ | — |
| ClusterRole (operator RBAC) | ❌ | ✅ |
| ClusterRole (token-requester) | ❌ | ✅ |
| ClusterRoleBinding | ✅ (from SA config) | — |
| MutatingWebhookConfiguration | ❌ | ✅ |
| ValidatingWebhookConfiguration | ❌ | ✅ |
| Certificate (cert-manager) | ❌ | ✅ |
| CRDs | Via JOSDK generate → copy | ✅ |

---

## Generated `values.yaml` Structure (Quarkus Helm default)

Quarkus Helm puts everything under `app:`:

```yaml
app:
  image: ghcr.io/plasticity-of-cloud/kube-microvm-operator:0.0.1-rc1
  envs:
    microvm.aws.region: us-east-1
  ports:
    http: 8080
    https: 8443
  livenessProbe: ...
  readinessProbe: ...
  resources:
    requests: {cpu: 100m, memory: 256Mi}
    limits:   {cpu: 500m, memory: 512Mi}
```

The deployment template uses `{{ .Values.app.image }}` — this is the **key difference** from the
current hand-crafted chart which uses `{{ .Values.image.repository }}:{{ .Values.image.tag }}`.
Helm install command remains the same; the internal structure changes.

---

## Files to Change

| Action | File |
|--------|------|
| Add dependency | `operator-controller/pom.xml` |
| Add version to BOM | `pom.xml` |
| Add helm/k8s config | `operator-controller/src/main/resources/application.properties` |
| Create custom resources | `operator-controller/src/main/kubernetes/kubernetes.yml` |
| Create helm overrides | `operator-controller/src/main/helm/values.yaml` |
| Create helm notes | `operator-controller/src/main/helm/templates/NOTES.txt` |
| Update CI | `.github/workflows/native-build.yml` (helm-chart job) |
| Delete | `charts/kube-microvm-operator/` (entire directory) |
| Update | `deploy-local.sh` (chart path changes to `operator-controller/target/helm/kubernetes/`) |
| Update | `README.md` (helm install path stays the same — OCI registry URL unchanged) |

---

## Risks and Mitigations

| Risk | Mitigation |
|------|-----------|
| Generated `values.yaml` uses `app.image` instead of `image.repository`/`image.tag` | Document breaking change in release notes; update any user-facing docs |
| CRD generation order — JOSDK generates CRDs during `compile`, Helm packages during `package` | Use `maven-resources-plugin` to copy `target/kubernetes/*-v1.yml` into `src/main/kubernetes/` OR use `quarkus.operator-sdk.crd.generate-all=true` |
| Custom templates (NOTES.txt, cert-manager) must be in `src/main/helm/templates/` | Create the directory and place templates there — Quarkus Helm merges them |
| Helm release name collision on upgrade (old chart used different resource names) | Use `helm upgrade --force` on first upgrade; document in migration notes |
