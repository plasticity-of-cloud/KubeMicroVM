# AWS Lambda MicroVMs SDK Client

## Status

`lambda-microvms` is not yet published to the public botocore/boto3 repository or any official
AWS SDK release. The service model is distributed only inside the AWS CLI snap package.

## Service Model Source

The `service-2.json` and `paginators-1.json` bundled in `operator-aws-client/src/main/resources/codegen-resources/`
were copied from the local AWS CLI snap installation:

```
/snap/aws-cli/2277/aws/dist/awscli/botocore/data/lambda-microvms/2025-09-09/
```

The canonical public source for all AWS service models is the botocore repository:
- https://github.com/boto/botocore/tree/develop/botocore/data

`lambda-microvms` is not yet present there. When it is published, update the bundled model
by copying the new `service-2.json` and `paginators-1.json` and rebuilding `operator-aws-client`.

## Client Generation

The Java client is generated at build time by `software.amazon.awssdk:codegen-maven-plugin`
(same version as the AWS SDK for Java v2 used in the project) reading the botocore model directly.

Generated output: `operator-aws-client/target/generated-sources/sdk/`  
Root package: `ai.codriverlabs.microvm.aws.lambdamicrovms`

## API Details

| Property | Value |
|----------|-------|
| API version | `2025-09-09` |
| Protocol | `rest-json` |
| Endpoint prefix | `lambda` |
| Signing name | `lambda` |
| Endpoint pattern | `https://lambda.{region}.amazonaws.com/2025-09-09/...` |
| Operations | 24 |

The service shares the Lambda service endpoint (`lambda.{region}.amazonaws.com`) with a
versioned path prefix (`/2025-09-09/`) distinguishing MicroVMs operations from the standard
Lambda API.

## Regional Availability

As of 2026-06-28, the service is available in:

| Region | Status |
|--------|--------|
| `us-east-1` | ✅ Available |
| `us-east-2` | ✅ Available |
| `us-west-2` | ✅ Available |
| `eu-west-1` | ✅ Available |
| `eu-central-1` | ❌ Not available (403) |
| `ap-southeast-1` | ❌ Not available (403) |
| `ap-south-1` | ❌ Not available (403) |

**Implication**: the operator and the EKS cluster it runs on must be deployed in a supported
region. Cross-region deployments (e.g. operator in `ap-south-1`, MicroVMs in `us-east-1`)
are unsupported because MicroVM network connectors must be co-located with the MicroVM.

## Updating the Model

When AWS publishes `lambda-microvms` to botocore:

```bash
# Copy updated model files
cp <botocore>/data/lambda-microvms/2025-09-09/service-2.json \
   operator-aws-client/src/main/resources/codegen-resources/
cp <botocore>/data/lambda-microvms/2025-09-09/paginators-1.json \
   operator-aws-client/src/main/resources/codegen-resources/

# Rebuild the generated client
./mvnw -pl operator-aws-client clean install -DskipTests
```

The generated Java client will be regenerated automatically on every build — no manual
code changes are needed unless the API version changes.
