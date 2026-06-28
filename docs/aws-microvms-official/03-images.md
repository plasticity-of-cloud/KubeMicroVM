# AWS Lambda MicroVMs — Images
Source: https://docs.aws.amazon.com/lambda/latest/dg/microvms-images.html

## Sizing (Baseline → Peak)

| Baseline | Peak | Max Disk |
|----------|------|----------|
| 0.5 GB / 0.25 vCPU | 2 GB / 1 vCPU | 8 GB |
| 1 GB / 0.5 vCPU | 4 GB / 2 vCPU | 8 GB |
| 2 GB / 1 vCPU (default) | 8 GB / 4 vCPU | 8 GB |
| 4 GB / 2 vCPU | 16 GB / 8 vCPU | 16 GB |
| 8 GB / 4 vCPU | 32 GB / 16 vCPU | 32 GB |

Memory is set at image creation time via the `memory` parameter. vCPU scales proportionally (2 GB = 1 vCPU).

## Base Images

Lambda publishes managed MicroVM base images (Amazon Linux 2023). Default: `arn:aws:lambda:<region>:aws:microvm-image:al2023-1`

Base image lifecycle:
- **AVAILABLE** — Current, recommended
- **DEPRECATED** (60 days) — Newer version exists, still builds and runs
- **EXPIRING** (30 days) — Cannot create new images
- **EXPIRED** — Cannot build or run
- **RECALLED** — Critical security issue, immediately unavailable

Lambda base container image for Dockerfiles: `public.ecr.aws/lambda/microvms:al2023-minimal`

## Build Hooks (Image Creation)

| Hook | Path | Purpose | Success | Retry |
|------|------|---------|---------|-------|
| /ready | /aws/lambda-microvms/runtime/v1/ready | Signals app ready to snapshot | HTTP 200 | HTTP 503 (retry until timeout) |
| /validate | /aws/lambda-microvms/runtime/v1/validate | Confirms app works when resumed | HTTP 200 | HTTP 503 (retry until timeout) |

**Important**: Return HTTP 503 immediately, do not hold the request open.

## Image States (Three Independent)

| State type | Values | Controlled by |
|-----------|--------|---------------|
| Image state | CREATING, CREATED, CREATION_FAILED, UPDATING, UPDATED, UPDATE_FAILED, DELETING, DELETED, DELETION_FAILED | Lambda (automatic) |
| Version state | PENDING, IN_PROGRESS, SUCCESSFUL, FAILED | Lambda (automatic) |
| Version activation | ACTIVE, INACTIVE | User (update-microvm-image-version) |

To run a MicroVM: image state must be CREATED or UPDATED + version state SUCCESSFUL + version ACTIVE.

## Container Base Images
Use any Linux container image in your Dockerfile FROM instruction. Requirements:
- Linux OS, target CPU architecture compatible
- Publicly accessible or ECR in same account
- Must be snapshot-compatible (review compatibility guide)
- Private ECR requires build role with `ecr:GetAuthorizationToken` + `ecr:BatchGetImage`

## OS Capabilities
Default: standard Linux capabilities. Elevated: `additionalOsCapabilities: ["ALL"]`
Enables: mounting filesystems, network namespaces, eBPF programs.
Contained within VM isolation boundary — does not affect host.

## Updating Images
`update-microvm-image` triggers a new version build. `--base-image-arn` and `--build-role-arn` are REQUIRED on every update call.

Update reasons:
- Deploy new application code (new S3 zip)
- Upgrade base image (security patches)
- Change build role permissions
- Adjust runtime configuration (hooks, env vars, capabilities)

## Environment Variables
Set at image build time via `environmentVariables` field (max 50). Injected during snapshot build. Shared across all MicroVMs from the image. Use `runHookPayload` for per-MicroVM configuration.
