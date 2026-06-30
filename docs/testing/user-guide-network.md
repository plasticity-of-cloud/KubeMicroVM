# User Guide: MicroVMNetwork

## Overview

A `MicroVMNetwork` resource manages an AWS Lambda MicroVM Network Connector,
providing VPC networking (egress) to MicroVMs. Network connectors enable MicroVMs
to communicate with resources in your VPC, including databases, caches, and
internet access via NAT gateways.

## Prerequisites

- A VPC with at least one subnet
- A security group allowing outbound traffic
- The KubeMicroVM operator running with appropriate IAM permissions

## Creating a Network

```yaml
apiVersion: lambda.aws.amazon.com/v1alpha1
kind: MicroVMNetwork
metadata:
  name: my-vpc-network
  namespace: default
spec:
  subnetIds:
    - subnet-0fdc8b729163e12a7
    - subnet-0bde13101743f4751
  securityGroupIds:
    - sg-01032cc226cb1615d
  networkProtocol: TCP
```

Apply:
```bash
kubectl apply -f my-network.yaml
```

## Checking Status

```bash
kubectl get microvmnetwork my-vpc-network -o yaml
```

Status fields:
- `connectorArn` — the AWS ARN of the created network connector
- `connectorId` — unique identifier
- `connectorState` — `PENDING`, `ACTIVE`, `FAILED`, `DELETING`

The network is ready when `connectorState: ACTIVE`.

## Using a Network with MicroVMs

Reference the network in your MicroVM spec:

```yaml
apiVersion: lambda.aws.amazon.com/v1alpha1
kind: MicroVM
metadata:
  name: my-app-vm
  namespace: default
spec:
  imageRef: "arn:aws:lambda:us-east-1:123456789012:microvm-image:my-app"
  imageVersion: "1.0"
  className: "micro"
  desiredState: "Running"
  networkRef: my-vpc-network
```

Or reference the connector ARN directly:

```yaml
spec:
  egressNetworkConnectors:
    - "arn:aws:lambda:us-east-1:123456789012:network-connector:my-connector"
```

## Updating a Network

Modify the spec (e.g., add a security group) and re-apply:

```bash
kubectl apply -f my-network.yaml
```

> **Note**: Updates may require MicroVMs to be restarted if they are currently
> using the network connector.

## Deleting a Network

```bash
kubectl delete microvmnetwork my-vpc-network
```

The operator will:
1. Check if any MicroVMs reference this network
2. If MicroVMs are attached — block deletion (finalizer held)
3. If no MicroVMs — delete the AWS network connector, then remove the CR

## Troubleshooting

**State: FAILED**
- Check `status.conditions` for the error message
- Common causes: invalid subnet ID, security group not in the same VPC,
  insufficient IAM permissions

**Deletion stuck**
- Check if MicroVMs still reference this network:
  ```bash
  kubectl get microvms -A -o json | jq '.items[] | select(.spec.networkRef == "my-vpc-network") | .metadata.name'
  ```
- Terminate those MicroVMs first, then retry deletion

## IAM Permissions Required

The operator role needs:
```
lambda:CreateNetworkConnector
lambda:GetNetworkConnector
lambda:UpdateNetworkConnector
lambda:DeleteNetworkConnector
lambda:ListNetworkConnectors
lambda:PassNetworkConnector
```

Scoped to: `arn:aws:lambda:<region>:<account>:network-connector:*`
