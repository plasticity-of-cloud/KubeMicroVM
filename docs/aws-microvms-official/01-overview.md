# AWS Lambda MicroVMs — Overview
Source: https://docs.aws.amazon.com/lambda/latest/dg/lambda-microvms-guide.html

AWS Lambda MicroVMs are serverless compute environments that provide VM-level isolation with full OS capabilities, snapshot-based rapid startup, and fine-grained networking control.

## Use Cases
- **Interactive code environments** — Development environments where users write and execute code in real time
- **AI code execution sandboxes** — Ephemeral sandboxes for executing AI-generated code safely
- **Data analytics applications** — Jupyter notebooks and ephemeral data processing workloads
- **Security scanning** — Vulnerability assessment tools that need isolated execution environments
- **Reinforcement learning environments** — Isolated sandboxes for AI agent evaluation and training
- **Multi-tenant CI/CD** — Task executors that require isolation between tenants
- **Game servers** — Hosting environments that execute user-supplied scripts with strong isolation

## How It Works
1. Package application code + Dockerfile into a zip, upload to S3
2. Call Lambda API to create a MicroVM image — Lambda executes Dockerfile, starts app, captures Firecracker snapshot
3. Call `run-microvm` — Lambda launches MicroVM from snapshot with rapid startup
4. Clients connect via dedicated HTTPS endpoint (no load balancers needed)
5. MicroVM suspends when idle (preserves memory + disk state), resumes when traffic returns
6. Call `terminate-microvm` to release resources

## Key Features
- **Rapid startup**: Resume from pre-initialized snapshot (sub-second per 500MB of snapshot data read)
- **Lifecycle control**: Suspend/resume/terminate programmatically or via idle policies
- **Flexible networking**: HTTPS inbound with JWE auth, configurable outbound (internet or VPC)
- **Vertical scaling**: 4x baseline burst (e.g. 2GB baseline → 8GB peak)
- **Max runtime**: 8 hours (28,800 seconds)

## Technology
Powered by Firecracker — same virtualization that powers 15 trillion+ monthly Lambda function invocations.
