package ai.codriverlabs.microvm.operator.cli.commands;

import ai.codriverlabs.microvm.aws.lambdamicrovms.LambdaMicrovmsClient;
import ai.codriverlabs.microvm.aws.lambdamicrovms.model.CreateMicrovmShellAuthTokenRequest;
import ai.codriverlabs.microvm.operator.core.model.MicroVM;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import software.amazon.awssdk.regions.Region;

import java.util.List;

/**
 * kubectl microvm exec — open a shell session in a running MicroVM.
 *
 * Prints the shell auth token and WebSocket endpoint so the user can connect
 * with a WebSocket client. Direct terminal multiplexing is outside the CLI scope.
 *
 * Usage:
 *   kubectl microvm exec my-vm
 *   kubectl microvm exec my-vm --show-token   # print raw token for scripting
 */
@Command(name = "exec", description = "Get shell access credentials for a running MicroVM",
        mixinStandardHelpOptions = true)
public class ExecCommand implements Runnable {

    @Parameters(index = "0", description = "Name of the MicroVM")
    String name;

    @Parameters(index = "1..*", description = "Command hint (for display only)", arity = "0..*")
    List<String> command;

    @Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "Namespace")
    String namespace;

    @Option(names = {"--expires"}, defaultValue = "30", description = "Token expiry in minutes")
    int expiresMinutes;

    @Option(names = {"--show-token"}, description = "Print raw shell auth token only")
    boolean showToken;

    @Option(names = {"--region"}, description = "AWS region")
    String region;

    @Inject
    KubernetesClient client;

    @Override
    public void run() {
        MicroVM vm = client.resources(MicroVM.class).inNamespace(namespace).withName(name).get();
        if (vm == null) {
            System.err.printf("MicroVM '%s' not found in namespace '%s'%n", name, namespace);
            System.exit(1);
            return;
        }
        if (vm.getStatus() == null || vm.getStatus().getMicroVmId() == null) {
            System.err.printf("MicroVM '%s' has no microvmId in status (state: %s)%n",
                    name, vm.getStatus() != null ? vm.getStatus().getState() : "unknown");
            System.exit(1);
            return;
        }

        String microvmId = vm.getStatus().getMicroVmId();
        String endpoint = vm.getStatus().getEndpointUrl();
        String awsRegion = region != null ? region
                : (vm.getSpec().getRegion() != null ? vm.getSpec().getRegion() : "us-east-1");

        try (LambdaMicrovmsClient awsClient = LambdaMicrovmsClient.builder()
                .region(Region.of(awsRegion)).build()) {

            var response = awsClient.createMicrovmShellAuthToken(
                    CreateMicrovmShellAuthTokenRequest.builder()
                            .microvmIdentifier(microvmId)
                            .expirationInMinutes(expiresMinutes)
                            .build());

            String token = response.authToken().getOrDefault("X-aws-proxy-auth",
                    response.authToken().values().iterator().next());

            if (showToken) {
                System.out.println(token);
            } else {
                System.out.printf("MicroVM: %s%n", name);
                System.out.printf("Endpoint: wss://%s/aws/lambda-microvms/runtime/v1/shell%n", endpoint);
                System.out.printf("Token (X-aws-proxy-auth): %s%n", token);
                System.out.println();
                System.out.println("Connect with a WebSocket client using subprotocols:");
                System.out.printf("  lambda-microvms%n");
                System.out.printf("  lambda-microvms.authentication.%s%n", token);
            }

        } catch (Exception e) {
            System.err.printf("Error creating shell token for MicroVM '%s': %s%n", name, e.getMessage());
            System.exit(1);
        }
    }
}
