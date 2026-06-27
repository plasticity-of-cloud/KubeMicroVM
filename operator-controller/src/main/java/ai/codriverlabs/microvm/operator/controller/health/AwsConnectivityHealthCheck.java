package ai.codriverlabs.microvm.operator.controller.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

@Liveness
@ApplicationScoped
public class AwsConnectivityHealthCheck implements HealthCheck {

    private static volatile boolean awsConnectivityConfirmed = false;
    private static volatile boolean informerCachesSynced = false;

    @Override
    public HealthCheckResponse call() {
        boolean ready = awsConnectivityConfirmed && informerCachesSynced;
        return HealthCheckResponse.named("aws-connectivity")
                .status(ready)
                .withData("awsConnectivity", awsConnectivityConfirmed)
                .withData("informerCachesSynced", informerCachesSynced)
                .build();
    }

    public static void setAwsConnectivityConfirmed(boolean confirmed) {
        awsConnectivityConfirmed = confirmed;
    }

    public static void setInformerCachesSynced(boolean synced) {
        informerCachesSynced = synced;
    }
}
