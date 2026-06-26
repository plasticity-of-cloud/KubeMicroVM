package ai.codriverlabs.microvm.operator.cli.commands;

import ai.codriverlabs.microvm.operator.core.model.MicroVMImage;
import ai.codriverlabs.microvm.operator.core.model.MicroVMImageStatus;
import ai.codriverlabs.microvm.operator.core.model.MicroVMImageVersionInfo;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.List;

/**
 * Polls a MicroVMImage until the latest version reaches a terminal state.
 * Prints state transitions as they happen.
 */
public class ImageWaiter {

    private static final long POLL_INTERVAL_MS = 2000;

    private final KubernetesClient client;
    private final String name;
    private final String namespace;
    private final int timeoutSeconds;

    public ImageWaiter(KubernetesClient client, String name, String namespace, int timeoutSeconds) {
        this.client = client;
        this.name = name;
        this.namespace = namespace;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Blocks until the latest image version reaches a terminal state or timeout.
     * Returns true if successful, false if failed/timed out.
     */
    public boolean waitForBuild() {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        String lastState = null;

        System.out.printf("⏳ Waiting for microvm-image/%s build (timeout: %ds)...%n", name, timeoutSeconds);

        while (System.currentTimeMillis() < deadline) {
            MicroVMImage image = client.resources(MicroVMImage.class)
                .inNamespace(namespace).withName(name).get();

            if (image == null) {
                System.err.printf("❌ MicroVMImage '%s' not found%n", name);
                return false;
            }

            MicroVMImageStatus status = image.getStatus();
            if (status != null && status.getVersions() != null && !status.getVersions().isEmpty()) {
                MicroVMImageVersionInfo latest = status.getVersions().getFirst();
                String currentState = latest.getState();

                if (currentState != null && !currentState.equals(lastState)) {
                    printTransition(lastState, currentState, latest);
                    lastState = currentState;
                }

                if (isTerminal(currentState)) {
                    return isSuccess(currentState);
                }
            }

            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Interrupted");
                return false;
            }
        }

        System.err.printf("❌ Timed out after %ds (last state: %s)%n", timeoutSeconds, lastState);
        return false;
    }

    private void printTransition(String from, String to, MicroVMImageVersionInfo version) {
        String arrow = from != null ? String.format("  %s → %s", from, to) : String.format("  → %s", to);
        String detail = switch (to) {
            case "Pending" -> "(queued for build)";
            case "InProgress" -> "(building image from Dockerfile)";
            case "Successful" -> String.format("(version %d ready)", version.getVersion());
            case "Active" -> String.format("(version %d activated — serving traffic)", version.getVersion());
            case "Failed" -> String.format("(build failed: %s)",
                version.getFailureReason() != null ? version.getFailureReason() : "unknown");
            default -> "";
        };
        System.out.printf("%s %s%n", arrow, detail);
    }

    private boolean isTerminal(String state) {
        return state != null && List.of("Successful", "Active", "Failed").contains(state);
    }

    private boolean isSuccess(String state) {
        if ("Failed".equals(state)) {
            System.out.println("❌ Build failed.");
            return false;
        }
        System.out.println("✅ Build completed successfully.");
        return true;
    }
}
