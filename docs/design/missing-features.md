# What's Missing — Implementation Gaps

This document tracks features that are designed (or partially designed) but not yet implemented.
It is the authoritative TODO for engineering work beyond the initial API coverage.

---

## 1. MicroVMNetwork Reconciler

**Status**: Model and design exist. Reconciler not implemented.

**What exists:**
- `MicroVMNetwork` / `MicroVMNetworkSpec` model classes
- Full design in `docs/design/networking.md`
- Webhook validates `networkRef` field on MicroVMSpec
- `MicroVMReconciler` reads `networkRef` and passes `connectorArn` to RunMicrovm (assumes status already populated)

**What's missing:**

| Gap | Detail |
|-----|--------|
| `MicroVMNetworkReconciler` | No reconciler class exists. Operator does not call `CreateNetworkConnector` / `DeleteNetworkConnector` / poll state |
| `MicroVMNetworkStatus` | No status class exists — `connectorArn`, `connectorState`, `conditions` are never written |
| Network Connector API client | `MicroVMNetworkClient` wrapper does not exist — the SDK methods exist but are not wired |
| IAM CloudFormation template | `iam/` has the operator role but no template for the customer's VPC ENI role (`operatorRoleArn`) |
| Webhook validation | Webhook currently only validates that `networkRef` is a non-empty string; it does not check the referenced CR exists or is Ready |
| CLI | `kubectl microvm network` subcommand does not exist |

**AWS APIs required:**
- `CreateNetworkConnector` — provisions ENIs in customer VPC subnets
- `GetNetworkConnector` — poll PENDING → ACTIVE / FAILED
- `DeleteNetworkConnector` — cleanup on CR delete
- `ListNetworkConnectors` — discovery / drift detection

**Unresolved design questions:**
- Should `MicroVMNetwork` be cluster-scoped or namespace-scoped? Currently `Namespaced`. Cross-namespace reference from a MicroVM needs a policy decision.
- Should the operator validate that subnets/SGs exist via `ec2:Describe*`? Adds EC2 IAM permissions to the operator role.

---

## 2. MicroVMReplicaSet (renamed from MicroVMPool)

**Status**: Model, status class, and design exist under the old name `MicroVMPool`. Reconciler not implemented. **All code and docs must be renamed to `MicroVMReplicaSet`.**

**Rename scope:**
- `MicroVMPool.java` → `MicroVMReplicaSet.java`
- `MicroVMPoolSpec.java` → `MicroVMReplicaSetSpec.java`
- `MicroVMPoolStatus.java` → `MicroVMReplicaSetStatus.java`
- `docs/design/pool.md` → `docs/design/replicaset.md`
- CRD group/kind: `MicroVMPool` → `MicroVMReplicaSet`
- Helm chart CRDs

**What's missing after rename:**

| Gap | Detail |
|-----|--------|
| `MicroVMReplicaSetReconciler` | No reconciler class exists |
| Scale-up logic | Create child MicroVM CRs from `spec.template` when `currentReplicas < spec.replicas` |
| Scale-down logic | Select victims by policy (MostRecentFirst / OldestFirst / Random), set `desiredState: Terminated` |
| Rolling update logic | Detect `spec.template` generation change, create new / drain old maintaining `minReady` |
| Health-based eviction | Replace FAILED / stuck-PENDING / unexpectedly-TERMINATED MicroVMs |
| `spec.desiredPoolState` | Cascade `Suspended` / `Running` to all children |
| `MicroVMReplicaSetStatus` | Populate `readyReplicas`, `currentReplicas`, `suspendedReplicas`, `updatedReplicas` |
| Integration tests | `MicroVMReplicaSetReconcilerIT` does not exist |
| CLI | `kubectl microvm rs` / `kubectl microvm replicaset` subcommand does not exist |

**`MicroVMReplicaSetSpec` fields needed beyond current `MicroVMPoolSpec`:**
```yaml
spec:
  replicas: 5
  template: <MicroVMSpec>
  minReady: 1              # exists
  maxSurge: 2              # exists
  scaleDown:
    policy: MostRecentFirst   # MISSING
    stabilizationWindowSeconds: 60  # MISSING
  desiredPoolState: Running   # MISSING (Running | Suspended)
```

---

## 3. Token Injection (Phase 1 — Operator Sub-Resource)

**Status**: Full design in `docs/design/roadmap/token-injection.md`. Not implemented.

**Problem**: `kubectl microvm token` requires AWS credentials in the calling process. Pods inside the cluster that need to connect to MicroVMs cannot call AWS directly (no credentials, no IAM role).

**Solution**: Operator exposes a REST sub-resource endpoint. Pods call the operator via Kubernetes RBAC (ServiceAccount token), operator calls AWS and returns the MicroVM auth token.

**What's missing:**

| Gap | Detail |
|-----|--------|
| `/tokens` REST endpoint in operator | Quarkus REST endpoint: `POST /apis/lambda.aws.amazon.com/v1alpha1/namespaces/{ns}/microvms/{name}/tokens` |
| Token request CRD (optional) | Alternative: `MicroVMTokenRequest` CR (like `CertificateSigningRequest`) |
| RBAC policy | New ClusterRole `microvm-token-requester` allowing `create` on `microvms/tokens` subresource |
| CLI integration | `kubectl microvm token` should fall back to operator sub-resource when no AWS credentials in environment |
| Helm chart | ServiceAccount + RBAC for pods that need token access |

---

## 4. GraalVM Native CLI Binary

**Status**: `operator-cli/pom.xml` has a `native` Maven profile. Not built or distributed.

**What's missing:**

| Gap | Detail |
|-----|--------|
| Native build CI | GitHub Actions matrix: `linux/amd64` + `linux/arm64` (mandatory for macOS Apple Silicon users) |
| `install-kubectl-plugin.sh` native mode | Currently installs JVM runner JAR; needs to detect and install native binary when available |
| GraalVM reflection config | Quarkus auto-generates most of it, but AWS SDK model classes likely need manual `reflect-config.json` entries |
| Krew manifest | `plugins/kubectl-microvm.yaml` for Krew index submission |
| GHCR release artifact | Native binaries attached to GitHub Release as `kubectl-microvm-linux-amd64`, `kubectl-microvm-linux-arm64`, `kubectl-microvm-darwin-arm64` |

---

## 5. Helm Chart Publishing

**Status**: Chart exists at `charts/lambda-vm-ack-operator/`. Never published to GHCR OCI registry.

**What's missing:**

| Gap | Detail |
|-----|--------|
| `helm push` to GHCR | `oci://ghcr.io/plasticity-of-cloud/helm/kube-microvm-operator` referenced in README but empty |
| GitHub Actions release workflow | On tag push: build image → push ECR → package chart → push GHCR |
| Chart versioning | `Chart.yaml` version must be bumped in lockstep with operator image tag |
| CRD versioning | Chart CRDs need `helm.sh/chart-version` annotation for upgrade safety |

---

## Priority Order

| # | Feature | Effort | Value |
|---|---------|--------|-------|
| 1 | **MicroVMReplicaSet rename + reconciler** | Large | Core functionality — pool management |
| 2 | **MicroVMNetwork reconciler** | Medium | Required for VPC egress |
| 3 | **Token injection Phase 1** | Medium | Required for in-cluster pod access |
| 4 | **GraalVM native CLI + distribution** | Medium | Required for production CLI distribution |
| 5 | **Helm chart publishing** | Small | Required for anyone else to install |
