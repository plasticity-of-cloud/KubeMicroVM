# AWS Lambda MicroVMs — Getting Started
Source: https://docs.aws.amazon.com/lambda/latest/dg/microvms-getting-started.html

## Prerequisites

1. S3 bucket in the same region as the MicroVM image
2. IAM build role trusted by `lambda.amazonaws.com` with `sts:AssumeRole` + `sts:TagSession`

Build role permissions:
```json
{
  "Statement": [
    {"Effect": "Allow", "Action": ["s3:GetObject"], "Resource": "arn:aws:s3:::<bucket>/*"},
    {"Effect": "Allow", "Action": ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"],
     "Resource": "arn:aws:logs:<region>:<account>:log-group:/aws/lambda/microvms/*"}
  ]
}
```

## Step-by-Step

### 1. Write app + Dockerfile
Dockerfile FROM sets the container image for your app layers. Lambda base image is set separately via `--base-image-arn`.

```dockerfile
FROM public.ecr.aws/lambda/microvms:al2023-minimal
# OR your own base:
# FROM public.ecr.aws/eks-distro-build-tooling/eks-distro-minimal-base-nodejs:16-al23
WORKDIR /app
COPY app.js .
EXPOSE 8080
CMD ["node", "app.js"]
```

### 2. Package and upload
```bash
zip app.zip app.js Dockerfile
aws s3 cp app.zip s3://<bucket>/app.zip
```

### 3. Create MicroVM image
```bash
aws lambda-microvms create-microvm-image \
  --name my-first-microvm-image \
  --code-artifact uri=s3://<bucket>/app.zip \
  --base-image-arn arn:aws:lambda:us-east-1:aws:microvm-image:al2023-1 \
  --build-role-arn arn:aws:iam::<account>:role/MicrovmBuildRole
```

Build logs: CloudWatch `/aws/lambda/microvms/<image-name>`

### 4. Run MicroVM
```bash
aws lambda-microvms run-microvm \
  --image-identifier my-first-microvm-image \
  --ingress-network-connectors "arn:aws:lambda:us-east-1:aws:network-connector:aws-network-connector:ALL_INGRESS" \
  --egress-network-connectors "arn:aws:lambda:us-east-1:aws:network-connector:aws-network-connector:INTERNET_EGRESS" \
  --idle-policy '{"autoResumeEnabled":true,"maxIdleDurationSeconds":900,"suspendedDurationSeconds":300}'
```

Response: `microvmId` + `endpoint` URL.

### 5. Connect
```bash
# Create auth token
aws lambda-microvms create-microvm-auth-token \
  --microvm-identifier <mvm-id> \
  --expiration-in-minutes 30 \
  --allowed-ports '[{"allPorts":{}}]'

# Send request
curl https://<endpoint>/ -H "X-aws-proxy-auth: <TOKEN>"
```

### 6. Clean up
```bash
aws lambda-microvms terminate-microvm --microvm-identifier <mvm-id>
```
