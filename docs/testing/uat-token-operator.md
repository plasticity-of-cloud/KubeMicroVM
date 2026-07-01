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
