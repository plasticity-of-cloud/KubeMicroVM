# EKS Deployment Guidelines

## Development Testing Workflow

When deploying to the EKS test cluster during development:

1. **Always force-uninstall the old chart first** before deploying a new version:
   ```bash
   helm uninstall kube-microvm-operator -n kube-microvm --wait
   # Remove any stuck resources (finalizers can block namespace cleanup)
   kubectl delete microvmimages,microvms,microvmreplicasets,microvmnetworks -A --all --force 2>/dev/null
   ```

2. **Only then deploy the new chart**:
   ```bash
   helm install kube-microvm-operator <chart> -n kube-microvm ...
   ```

3. **Always use `imagePullPolicy: Always`** to ensure the latest image is pulled,
   even when reusing the same tag (e.g. `0.0.1-rc1`):
   ```bash
   --set app.imagePullPolicy=Always
   ```

## Rationale

- `helm upgrade` during development often leaves stale resources (old secrets, CRDs, finalizers)
  that conflict with the new deployment.
- With a fixed tag like `0.0.1-rc1`, Kubernetes defaults to `IfNotPresent` and won't
  pull the updated image unless `imagePullPolicy: Always` is explicitly set.
- cert-manager Certificates and Secrets must be re-created when the issuer chain changes.
