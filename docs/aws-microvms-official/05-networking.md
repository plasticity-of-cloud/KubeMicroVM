# AWS Lambda MicroVMs — Networking
Source: https://docs.aws.amazon.com/lambda/latest/dg/microvms-networking.html

## Overview
Network access is configured at `run-microvm` time via Network Connector ARNs. Cannot change while MicroVM is running.

- **Ingress**: AWS-managed HTTPS endpoint, routes traffic to ports inside MicroVM
- **Egress**: Default = public internet. Custom = VPC egress connector

A single connector can be reused across many MicroVMs.

## Inbound Connectivity

Each MicroVM gets a unique public HTTPS endpoint. Default port routing: 8080.

### Supported Protocols
- HTTP/1.1, HTTP/2, WebSockets, gRPC, Server-Sent Events (SSE)
- TLS always enforced between client and endpoint

### Port Routing Priority
1. `X-aws-proxy-port` header
2. WebSocket subprotocol `lambda-microvms.port.N`
3. Default: 8080

### Authentication
JWE token in `X-aws-proxy-auth` header. Token scoped to MicroVM ID + ports + expiration.

### Request/Response Bandwidth

| MicroVM size | Max bandwidth |
|-------------|---------------|
| 0.5 GB / 0.25 vCPU | 1 MB/s (8 Mbps) |
| 1 GB / 0.5 vCPU | 2 MB/s (16 Mbps) |
| 2 GB / 1 vCPU | 4 MB/s (32 Mbps) |
| 4 GB / 2 vCPU | 8 MB/s (64 Mbps) |
| 8 GB / 4 vCPU | 16 MB/s (128 Mbps) |

### HTTP Error Codes from Endpoint

| Code | Cause |
|------|-------|
| 400 | Malformed request, invalid port header |
| 403 | Missing/expired/invalid token, or unauthorized port |
| 429 | Rate limit exceeded |
| 500 | Internal error |
| 502 | App not responding or auto-resume failed |

### Lambda-Managed Connectors
- `ALL_INGRESS`: Enable inbound HTTPS on all ports
- `INTERNET_EGRESS`: Enable public internet egress
- `NO_INGRESS`: Disable inbound connectivity

ARN format: `arn:aws:lambda:<region>:aws:network-connector:aws-network-connector:<TYPE>`

## Outbound / Egress

Default: public internet access.

For VPC egress (RDS, ElastiCache, internal APIs, Direct Connect): create a customer-managed VPC egress connector via `aws lambda-core create-network-connector`.

### VPC Connector Prerequisites
IAM role for Lambda to create ENIs:
```json
{
  "Statement": [
    {"Effect": "Allow", "Action": "ec2:CreateNetworkInterface",
     "Resource": ["arn:aws:ec2:*:*:network-interface/*", "arn:aws:ec2:*:*:subnet/*", "arn:aws:ec2:*:*:security-group/*"]},
    {"Effect": "Allow", "Action": "ec2:CreateTags",
     "Resource": "arn:aws:ec2:*:*:network-interface/*",
     "Condition": {"StringEquals": {"ec2:ManagedResourceOperator": "network-connectors.lambda.amazonaws.com"}}}
  ]
}
```

### Connector States

| State | Description |
|-------|-------------|
| PENDING | ENIs being provisioned |
| ACTIVE | Ready to use |
| INACTIVE | Temporarily inactive |
| FAILED | Check StateReason |
| DELETING | ENIs being cleaned up |

**Important**: Before updating or deleting a connector, ensure all MicroVMs using it have terminated.
