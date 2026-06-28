# AWS Lambda MicroVMs — Core Concepts
Source: https://docs.aws.amazon.com/lambda/latest/dg/microvms-how-it-works.html

## Resource Types

### MicroVM
A MicroVM is an isolated compute environment for a single tenant, user session, or job.
- Runs Amazon Linux 2023 OS
- Receives requests through inbound HTTPS connections
- Can be suspended when idle (preserves memory + disk state)
- Resumes when traffic returns

### MicroVM Image
Defines the application environment. Created from a Dockerfile + artifacts zip in S3.
- Versioned — each build is a new version
- Version states: PENDING → IN_PROGRESS → SUCCESSFUL | FAILED
- Successful versions can be ACTIVE or INACTIVE
- Must use a Lambda-managed base image as foundation (base-image-arn)

### Network Connectors
Control inbound and outbound network access.
- Ingress: AWS-managed, referenced by ARN
- Egress: AWS-managed (internet) or customer-managed (VPC)
- Connectors are reusable across many MicroVMs
- Set at `run-microvm` time, cannot be changed while running

## Build Process
1. Lambda provisions a fresh MicroVM from the managed base image
2. Executes Dockerfile instructions
3. Starts application via ENTRYPOINT or CMD
4. If `/ready` hook configured, waits for HTTP 200
5. Captures Firecracker snapshot of disk + memory state

**Important**: Content generated during build (unique IDs, secrets, network connections) is shared across all MicroVMs from that image. Use the `/run` lifecycle hook to generate unique content per MicroVM.

## MicroVM Lifecycle States

| State | Description |
|-------|-------------|
| PENDING | Being provisioned, snapshot loading |
| RUNNING | Active, accepting traffic |
| SUSPENDING | Checkpointing disk + memory |
| SUSPENDED | Paused, state preserved, no compute charges |
| TERMINATING | Releasing resources |
| TERMINATED | Terminal state, cannot be resumed |

## State Transitions

| From | To | Trigger |
|------|----|---------|
| PENDING | RUNNING | Provisioning complete, /run hook succeeded |
| RUNNING | SUSPENDING | Idle duration exceeded or explicit suspend-microvm |
| SUSPENDING | SUSPENDED | /suspend hook done, state checkpointed |
| SUSPENDED | RUNNING | Traffic arrives (autoResumeEnabled=true) or resume-microvm |
| RUNNING | TERMINATING | terminate-microvm call or maximumDurationInSeconds exceeded |
| SUSPENDED | TERMINATING | suspendedDurationSeconds exceeded or terminate-microvm |
| TERMINATING | TERMINATED | /terminate hook done, all resources released |
