# AWS Lambda MicroVMs — Running and Connecting
Source: https://docs.aws.amazon.com/lambda/latest/dg/microvms-launching.html

## Starting a MicroVM

```bash
aws lambda-microvms run-microvm \
  --image-identifier arn:aws:lambda:us-east-1:123456789012:microvm-image:my-image \
  --ingress-network-connectors "arn:aws:lambda:us-east-1:aws:network-connector:aws-network-connector:ALL_INGRESS" \
  --egress-network-connectors "arn:aws:lambda:us-east-1:aws:network-connector:aws-network-connector:INTERNET_EGRESS" \
  --idle-policy '{"autoResumeEnabled":true,"maxIdleDurationSeconds":900,"suspendedDurationSeconds":1800}' \
  --maximum-duration-in-seconds 14400
```

### Key Parameters

| Parameter | Description |
|-----------|-------------|
| --image-identifier | (Required) ARN of the MicroVM image |
| --image-version | Specific version, defaults to latest active |
| --execution-role-arn | IAM role for MicroVM to call other AWS services |
| --idle-policy | Automatic suspend/resume behavior |
| --maximum-duration-in-seconds | Max lifetime: 1–28,800s (8 hours) |
| --run-hook-payload | Per-MicroVM config string (max 16 KB) |
| --logging | CloudWatch log group/stream config |
| --ingress-network-connectors | ARN(s) for inbound HTTPS |
| --egress-network-connectors | ARN(s) for outbound (internet or VPC) |

### Idle Policy

| Field | Description |
|-------|-------------|
| autoResumeEnabled | Auto-resume when traffic arrives while suspended |
| maxIdleDurationSeconds | Seconds without traffic before suspend (max 28,800) |
| suspendedDurationSeconds | Seconds suspended before auto-terminate |

### Runtime Payloads (run-hook-payload)
Per-MicroVM config data (max 16 KB). Delivered to `/run` hook as JSON:
```json
{"microvmId": "mvm-...", "runHookPayload": "tenant-specific-string"}
```
Use for: tenant IDs, session tokens, signed URLs, Secrets Manager paths.

## Connecting to a MicroVM

Endpoint format: `mvm-{id}.lambda-microvm.{region}.on.aws`

### Authentication
All requests require JWE token in `X-aws-proxy-auth` header. No unauthenticated access.

```bash
aws lambda-microvms create-microvm-auth-token \
  --microvm-identifier mvm-xxx \
  --expiration-in-minutes 30 \
  --allowed-ports '[{"allPorts":{}}]'

curl 'https://<endpoint>/' \
  -H 'X-aws-proxy-auth: <TOKEN>' \
  -H 'X-aws-proxy-port: 8080'
```

Token scopes: `{"port": N}`, `{"range": {"startPort": N, "endPort": N}}`, `{"allPorts": {}}`

### Protocols Supported
- HTTP/1.1, HTTP/2 (via ALPN)
- WebSockets
- gRPC
- Server-Sent Events (SSE)

WebSocket auth via subprotocols:
```javascript
const protocols = [
  "lambda-microvms",
  "lambda-microvms.authentication.<TOKEN>",
  "lambda-microvms.port.9000"
];
const ws = new WebSocket('wss://<endpoint>/path', protocols);
```

## Lifecycle Hooks (Runtime)

| Hook | Path | When | Purpose |
|------|------|------|---------|
| /run | /aws/lambda-microvms/runtime/v1/run | After start from snapshot | Initialize per-tenant state. Traffic begins after HTTP 200. |
| /resume | /aws/lambda-microvms/runtime/v1/resume | After resume from SUSPENDED | Re-establish connections, refresh credentials. |
| /suspend | /aws/lambda-microvms/runtime/v1/suspend | Before suspend | Flush writes, close connections. |
| /terminate | /aws/lambda-microvms/runtime/v1/terminate | Before terminate | Flush data, notify systems. |

## Suspend / Resume

### Suspend
1. **Automatic** via `maxIdleDurationSeconds` in idle policy
2. **Explicit** via `suspend-microvm` API

### Resume
- Explicit: `resume-microvm`
- Auto: when `autoResumeEnabled=true` and traffic arrives (Lambda holds the request while resuming)
- If resume fails: returns 502 Bad Gateway

## Scaling
- Each MicroVM = dedicated endpoint, no load balancing across MicroVMs
- Scale out = call `run-microvm` multiple times
- Account quota on total memory (RUNNING + SUSPENDED); request increase via Service Quotas

## Common Errors

| Error | Cause | Fix |
|-------|-------|-----|
| ServiceQuotaExceededException | Memory quota reached | Terminate idle MicroVMs or request quota increase |
| ResourceNotFoundException | Image not found or not in CREATED state | Verify image ARN and build status |
| ValidationException | Invalid parameters | Check idle policy values and connector ARNs |
| ThrottlingException | Rate limit exceeded | Exponential backoff with jitter |
