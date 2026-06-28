# AWS Lambda MicroVMs API — Implementation Status

Last updated: 2026-06-28

All 24 operations come from the botocore service model (`lambda-microvms/2025-09-09`).
See [sdk-client.md](sdk-client.md) for model provenance and endpoint details.

---

## MicroVM Lifecycle Operations

| API | Client method | Reconciler | CLI | Status |
|-----|---------------|------------|-----|--------|
| `RunMicrovm` | `DefaultMicroVMClient.runMicroVM()` | ✅ PENDING state | — | ✅ Complete |
| `GetMicrovm` | `DefaultMicroVMClient.getMicroVM()` | ✅ poll + drift | — | ✅ Complete |
| `SuspendMicrovm` | `DefaultMicroVMClient.suspendMicroVM()` | ✅ drift SUSPEND | `pause` | ✅ Complete |
| `ResumeMicrovm` | `DefaultMicroVMClient.resumeMicroVM()` | ✅ drift RESUME | `resume` | ✅ Complete |
| `TerminateMicrovm` | `DefaultMicroVMClient.terminateMicroVM()` | ✅ finalizer + TERMINATE drift | `delete` | ✅ Complete |
| `ListMicrovms` | ❌ not implemented | ❌ | `list` (k8s only) | ⚠️ CLI lists CRs, not AWS state |
| `CreateMicrovmAuthToken` | ❌ not implemented | ❌ | ❌ | ❌ **Missing — blocks connection** |
| `CreateMicrovmShellAuthToken` | ❌ not implemented | ❌ | ❌ | ❌ Missing (shell access) |
| `TagResource` | ❌ not implemented | ❌ | ❌ | ❌ Missing (tag propagation) |
| `UntagResource` | ❌ not implemented | ❌ | ❌ | ❌ Missing (tag propagation) |
| `ListTags` | ❌ not implemented | ❌ | ❌ | ❌ Missing |

---

## MicroVMImage Operations

| API | Client method | Reconciler | CLI | Status |
|-----|---------------|------------|-----|--------|
| `CreateMicrovmImage` | `MicroVMImageClient.createImage()` | ✅ first reconcile | `image create` | ✅ Complete |
| `UpdateMicrovmImage` | `MicroVMImageClient.updateImage()` | ✅ generation advance | `image update` | ✅ Complete |
| `GetMicrovmImage` | `MicroVMImageClient.getImage()` | ✅ poll image state | `image describe` | ✅ Complete |
| `DeleteMicrovmImage` | `MicroVMImageClient.deleteImage()` | ✅ finalizer | `image delete` | ✅ Complete |
| `GetMicrovmImageVersion` | `MicroVMImageClient.getImageVersion()` | ✅ poll version state | — | ✅ Complete |
| `UpdateMicrovmImageVersion` | ❌ not implemented | ❌ `autoActivate` unused | — | ❌ **Missing — autoActivate broken** |
| `DeleteMicrovmImageVersion` | ❌ not implemented | ❌ | — | ❌ Missing (version pruning) |
| `ListMicrovmImageVersions` | ❌ not implemented | ❌ `status.versions[]` empty | `image describe` | ⚠️ Partial — only latest version tracked |
| `ListMicrovmImages` | ❌ not implemented | ❌ | `image list` (k8s only) | ⚠️ CLI lists CRs, not AWS state |
| `GetMicrovmImageBuild` | ❌ not implemented | ❌ | — | ❌ Missing (build log access) |
| `ListMicrovmImageBuilds` | ❌ not implemented | ❌ | — | ❌ Missing |

---

## Managed Base Image Discovery

| API | Status | Notes |
|-----|--------|-------|
| `ListManagedMicrovmImages` | ❌ not implemented | Used in `setup-test-env.sh` via AWS CLI directly |
| `ListManagedMicrovmImageVersions` | ❌ not implemented | — |

---

## Priority Backlog

### P0 — Blocks end-to-end testing

**`CreateMicrovmAuthToken`**
- Without this, clients cannot connect to a running MicroVM (all endpoints require JWE token)
- Needed for: `kubectl microvm token` command, operator-surfaced connection info
- `CreateMicrovmAuthTokenRequest` fields: `microvmIdentifier`, `expirationInMinutes`, `allowedPorts`
- Response: `authToken` map (`X-aws-proxy-auth` header value)

### P1 — Feature correctness

**`UpdateMicrovmImageVersion` (activate/deactivate)**
- `MicroVMImageSpec.autoActivate=true` is stored but never acted upon
- After a version reaches `SUCCESSFUL`, the reconciler should call `UpdateMicrovmImageVersion --state ACTIVE`
- Currently Lambda auto-activates new versions, so this may work without us, but explicit activation is required for controlled rollouts

**`ListMicrovmImageVersions`**
- `status.versions[]` is defined in the CRD but never populated
- Needed for: `kubectl microvm image describe` showing version history

### P2 — Operational completeness

**Tag propagation (`TagResource` / `UntagResource`)**
- Sync `MicroVM` CR `.metadata.labels` → AWS resource tags
- Enables cost allocation, filtering by team/env in AWS console

**`ListMicrovms`**
- Currently `kubectl microvm list` only shows Kubernetes CRs
- Should reconcile AWS state with CR state (detect orphaned MicroVMs)

### P3 — Nice to have

**`CreateMicrovmShellAuthToken`**
- Powers `kubectl microvm exec` with native shell access
- Lower priority — gRPC/HTTP access covers most use cases

**`GetMicrovmImageBuild` / `ListMicrovmImageBuilds`**
- Exposes build logs and build history in `kubectl microvm image describe`
- Currently users must check CloudWatch directly

---

## Summary

| Category | Total | Implemented | Partial | Missing |
|----------|-------|-------------|---------|---------|
| MicroVM lifecycle | 11 | 5 | 2 | 4 |
| MicroVMImage | 11 | 5 | 2 | 4 |
| Base image discovery | 2 | 0 | 0 | 2 |
| **Total** | **24** | **10** | **4** | **10** |
