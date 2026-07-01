# UAT: Token via Operator Endpoint (in-cluster)

## What

A pod inside the cluster calls the operator REST endpoint to get a MicroVM auth token
without needing AWS credentials. The operator validates the caller's Kubernetes
ServiceAccount token and proxies the call to AWS.

Flow:
```
Pod (SA token) → POST /...microvms/{name}/token → Operator
                                                  → TokenReview (k8s)
                                                  → SubjectAccessReview (k8s)
                                                  → AWS CreateMicrovmAuthToken
                                                  ← auth token
```

## Test Cases

| ID | Test | Expected |
|----|------|----------|
| TOK-OP-01 | Pod with correct RBAC calls operator token endpoint | Returns MicroVM auth token |
| TOK-OP-02 | Pod WITHOUT RBAC gets rejected | 403 Forbidden |
| TOK-OP-03 | Token from operator endpoint works to call MicroVM | `{"status":"ok"}` |

## Prerequisites

- Operator running on EKS with HTTPS (port 8443) working
- A running MicroVM to get a token for
- Validating webhook deleted

## Acceptance Criteria

- Token endpoint reachable at operator service on port 443
- RBAC enforced: only SAs with `create microvms/token` can call it
- Token returned is valid for MicroVM endpoint

## Results (2026-07-01, EKS Auto Mode, us-east-1)

### Bug fixed: operator SA missing tokenreviews/subjectaccessreviews RBAC

The operator's ClusterRole was missing permissions to call `authentication.k8s.io/tokenreviews`
and `authorization.k8s.io/subjectaccessreviews`. Added `kube-microvm-operator-token-auth-role`
ClusterRole + ClusterRoleBinding to `kubernetes.yml` (included in Helm chart).

### Test Results

| ID | Test | Result | Notes |
|----|------|--------|-------|
| TOK-OP-01 | Authorized SA → POST token endpoint | ✅ PASS | JWT token returned with endpoint + expiresAt |
| TOK-OP-02 | Unauthorized SA → POST token endpoint | ✅ PASS | 403 Forbidden |
| TOK-OP-03 | Use operator token to call MicroVM | ✅ PASS | `{"status":"ok"}` from MicroVM echo server |

Token response example:
```json
{
  "authToken": "eyJraWQiOiJhZTQyYWQ2...",
  "endpoint": "5ccf8fc0-8b4c-7631-02ae-38318f1f989e.lambda-microvm.us-east-1.on.aws",
  "expiresAt": "2026-07-01T22:49:08.494188373Z"
}
```
