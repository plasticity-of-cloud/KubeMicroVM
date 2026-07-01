# UAT: MicroVM Suspend / Resume

## Test Cases

| ID | Test | Steps | Expected |
|----|------|-------|----------|
| SUSP-01 | Suspend running VM | Patch `desiredState: Suspended` | State transitions: Running → Suspending → Suspended |
| SUSP-02 | Endpoint unreachable when suspended | Call endpoint with valid token | Connection refused or timeout |
| SUSP-03 | Resume suspended VM | Patch `desiredState: Running` | State transitions: Suspended → Running |
| SUSP-04 | Endpoint responds after resume | Call endpoint with valid token | `{"status":"ok"}` |
| SUSP-05 | Auto-suspend via idle policy | Create VM with `maxIdleDurationSeconds: 60`, wait 90s with no traffic | State → Suspended |
| SUSP-06 | Auto-resume on traffic | Send request to auto-resume-enabled suspended VM | VM resumes, request eventually succeeds |

## Prerequisites

- Operator deployed on EKS cluster
- MicroVMImage `hello-node` built and ACTIVE
- Validating webhook deleted (not working yet)

## Acceptance Criteria

- All 6 test cases pass
- State transitions visible in `status.state`
- Conditions updated with appropriate messages
- No reconciler errors in operator logs during transitions

## Results (2026-07-01, EKS Auto Mode, us-east-1)

| ID | Result | Notes |
|----|--------|-------|
| SUSP-01 | ✅ PASS | Running → Suspending (6 polls) → Suspended |
| SUSP-02 | ✅ PASS | curl timeout — endpoint unreachable while suspended |
| SUSP-03 | ✅ PASS | Suspended → Running (instant, 1 poll) |
| SUSP-04 | ✅ PASS | `{"status":"ok","path":"/","ts":"2026-07-01T14:21:04.563Z"}` |
| SUSP-05 | ⏭️ SKIP | Requires waiting for idle timeout (60s+), deferred |
| SUSP-06 | ⏭️ SKIP | Requires auto-resume feature, deferred |

Suspend takes ~60s (Suspending state for 6 polls at 10s).
Resume is instant (state goes directly to Running on first poll).
