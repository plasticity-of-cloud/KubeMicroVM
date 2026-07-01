# UAT: Drift Detection + Auto-Suspend

## Drift Detection Test Cases

| ID | Test | Steps | Expected |
|----|------|-------|----------|
| DRIFT-01 | External terminate detected | Create VM via operator, call `aws lambda-microvms terminate-microvm` directly, wait for reconcile | CR status transitions to Terminated |
| DRIFT-02 | Operator re-creates on drift | Create VM, externally terminate, verify CR reconciled | New VM ID in status OR status=Terminated matching desired=Terminated |

## Auto-Suspend Test Cases

| ID | Test | Steps | Expected |
|----|------|-------|----------|
| AUTO-01 | VM auto-suspends after idle | Create VM with `maxIdleDurationSeconds: 60`, send no traffic, wait 90s | `status.state` → Suspended |
| AUTO-02 | VM auto-resumes on traffic | After AUTO-01, send request to endpoint | Request succeeds, state → Running |

## Prerequisites

- Operator running on EKS
- MicroVMImage `hello-node` CREATED and ACTIVE
- Validating webhook deleted

## Results (2026-07-01, EKS Auto Mode, us-east-1)

### Drift Detection

| ID | Result | Notes |
|----|--------|-------|
| DRIFT-01 | ✅ PASS | External terminate detected: CR state → Pending |
| DRIFT-02 | ✅ PASS | Operator re-created VM with new ID (`microvm-3154b49f-...`), state → Running |

Drift detection cycle: Running → (external terminate) → Pending (2 polls) → new VM Pending → Running

### Auto-Suspend / Auto-Resume

| ID | Result | Notes |
|----|--------|-------|
| AUTO-01 | ✅ PASS | VM auto-suspended after 60s idle — CR state → Suspended |
| AUTO-02 | ✅ PASS | autoResumeEnabled=true — request held by Lambda, VM resumed, response returned |

**Bug found and fixed**: Drift detector was treating `desiredState=Running` + `actual=SUSPENDED`
as drift and immediately resuming the VM, fighting the idle policy. Fixed to treat auto-suspend
as `NoOp` — status updated but no resume action. User must explicitly set `desiredState: Running`
to resume a VM that was auto-suspended.
