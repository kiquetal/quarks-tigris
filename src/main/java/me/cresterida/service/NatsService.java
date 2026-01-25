package me.cresterida.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import io.quarkus.runtime.StartupEvent;
import me.cresterida.dto.FileUploadEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StorageType;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StreamInfo;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.Optional;

/**
 * Service for publishing file upload events to NATS JetStream.
 *
 * Publishes minimal metadata (S3 location) about uploaded files so downstream services
 * (e.g., F# processing service) can download the full metadata and encrypted files from S3.
 */
@ApplicationScoped
public class NatsService {

    @Inject
    @Channel("file-uploads")
    Emitter<String> fileUploadEmitter;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "quarkus.messaging.nats.devservices.enabled")
    Optional<Boolean> devServicesEnabled;

    @ConfigProperty(name = "quarkus.messaging.nats.devservices.port")
    Optional<Integer> devServicesPort;

    @ConfigProperty(name = "quarkus.messaging.nats.connection.servers")
    Optional<String> natsServers;

    /**
     * Logs NATS configuration on startup and sends a test message
     */
    void onStart(@Observes StartupEvent ev) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("NATS Configuration on Startup:");
        System.out.println("  DevServices Enabled: " + devServicesEnabled.orElse(false));
        System.out.println("  DevServices Port: " + devServicesPort.map(String::valueOf).orElse("default"));
        System.out.println("  Connection Servers: " + natsServers.orElse("NOT CONFIGURED - using DevServices"));
        System.out.println("  Expected NATS URL: " + getNatsUrl());
        System.out.println("  Channel: file-uploads");
        System.out.println("  Subject: file.uploads");
        System.out.println("  Stream: FILE_UPLOADS");
        System.out.println("=".repeat(80) + "\n");

        // Create the stream programmatically
        createStreamIfNotExists();

        // Send a simple test message to verify NATS connection
        // NOTE: Disabled automatic test - use /api/nats-test/publish endpoint instead
        // This prevents errors on startup before the stream is created
        // sendTestMessage();
    }

    /**
     * Sends a simple test message to verify NATS is working
     */
    private void sendTestMessage() {
        try {
            String testJson = "{\"test\":\"message\",\"timestamp\":" + System.currentTimeMillis() + ",\"status\":\"startup_test\"}";

            System.out.println("📤 Sending test message to NATS...");
            System.out.println("   Test JSON: " + testJson);

            fileUploadEmitter.send(testJson)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        System.err.println("❌ TEST FAILED - Cannot connect to NATS!");
                        System.err.println("   Error: " + error.getMessage());
                        System.err.println("   Possible causes:");
                        System.err.println("   - NATS server not running");
                        System.err.println("   - Wrong port configuration");
                        System.err.println("   - JetStream not enabled (need -js flag)");
                        System.err.println("   - Stream FILE_UPLOADS doesn't exist");
                        error.printStackTrace();
                    } else {
                        System.out.println("✅ TEST SUCCESS - NATS connection working!");
                        System.out.println("   Message published successfully to file.uploads");
                    }
                });
        } catch (Exception e) {
            System.err.println("❌ TEST FAILED - Exception during test message:");
            e.printStackTrace();
        }
    }

    /**
     * Creates the FILE_UPLOADS stream programmatically if it doesn't exist.
     * This is necessary because DevServices doesn't auto-create streams.
     */
    private void createStreamIfNotExists() {
        Connection nc = null;
        try {
            // Determine NATS URL
            String natsUrl = natsServers.orElse("nats://localhost:" + devServicesPort.orElse(4222));

            System.out.println("🔧 Attempting to create NATS stream...");
            System.out.println("   Connecting to: " + natsUrl);

            // Connect to NATS with authentication if needed
            Options options = new Options.Builder()
                .server(natsUrl)
                .connectionTimeout(Duration.ofSeconds(5))
                .userInfo("guest", "guest")  // DevServices default credentials
                .build();

            nc = Nats.connect(options);
            System.out.println("   ✓ Connected to NATS server");

            // Get JetStream management context
            JetStreamManagement jsm = nc.jetStreamManagement();

            // Check if stream already exists
            try {
                StreamInfo streamInfo = jsm.getStreamInfo("FILE_UPLOADS");
                System.out.println("   ✓ Stream FILE_UPLOADS already exists");
                System.out.println("     Messages: " + streamInfo.getStreamState().getMsgCount());
                System.out.println("     Subjects: " + streamInfo.getConfiguration().getSubjects());
            } catch (Exception e) {
                // Stream doesn't exist, create it
                System.out.println("   Stream FILE_UPLOADS does not exist, creating...");

                StreamConfiguration streamConfig = StreamConfiguration.builder()
                    .name("FILE_UPLOADS")
                    .subjects("file.uploads")
                    .storageType(StorageType.File)
                    .retentionPolicy(RetentionPolicy.Limits)
                    .maxAge(Duration.ofDays(7))
                    .build();

                StreamInfo streamInfo = jsm.addStream(streamConfig);
                System.out.println("   ✅ Stream FILE_UPLOADS created successfully!");
                System.out.println("     Name: " + streamInfo.getConfiguration().getName());
                System.out.println("     Subjects: " + streamInfo.getConfiguration().getSubjects());
                System.out.println("     Storage: " + streamInfo.getConfiguration().getStorageType());
                System.out.println("     Max Age: " + streamInfo.getConfiguration().getMaxAge());
            }

        } catch (Exception e) {
            System.err.println("   ⚠️  Failed to create/verify NATS stream: " + e.getMessage());
            System.err.println("   This is expected if NATS server is not running or JetStream is not enabled");
            System.err.println("   Application will continue, but NATS publishing will fail");
            e.printStackTrace();
        } finally {
            // Close connection
            if (nc != null) {
                try {
                    nc.close();
                    System.out.println("   ✓ NATS connection closed");
                } catch (Exception e) {
                    System.err.println("   ⚠️  Error closing NATS connection: " + e.getMessage());
                }
            }
        }
        System.out.println("=".repeat(80) + "\n");
    }

    private String getNatsUrl() {
        if (natsServers.isPresent()) {
            return natsServers.get();
        } else if (devServicesEnabled.orElse(false)) {
            int port = devServicesPort.orElse(4222);
            return "nats://localhost:" + port + " (DevServices will auto-configure actual port)";
        }
        return "UNKNOWN - Check NATS configuration!";
    }

    /**
     * Publishes a file upload event to NATS JetStream.
     * Contains only S3 location info - consumer can download full metadata from S3.
     *
     * @param event The file upload event containing S3 location
     */
    public void publishFileUploadEvent(FileUploadEvent event) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);

            System.out.println("=".repeat(80));
            System.out.println("Publishing file upload event to NATS:");
            System.out.println("  Event ID: " + event.eventId);
            System.out.println("  File UUID: " + event.fileUuid);
            System.out.println("  Email: " + event.email);
            System.out.println("  S3 Data Key: " + event.s3DataKey);
            System.out.println("  S3 Metadata Key: " + event.s3MetadataKey);
            System.out.println("  Bucket: " + event.bucketName);
            System.out.println("=".repeat(80));

            // Send to NATS JetStream (async) and handle completion
            fileUploadEmitter.send(eventJson)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        System.err.println("✗ Failed to publish event to NATS: " + error.getMessage());
                        System.err.println("  Event ID: " + event.eventId);
                        System.err.println("  This is expected if NATS server is not running");
                        error.printStackTrace();
                    } else {
                        System.out.println("✓ Event published to NATS successfully");
                        System.out.println("  Event ID: " + event.eventId);
                    }
                });

        } catch (Exception e) {
            // This catches JSON serialization errors
            System.err.println("✗ Failed to serialize event for NATS: " + e.getMessage());
            e.printStackTrace();
            // Don't throw - uploading to S3 should succeed even if NATS fails
        }
    }

    /**
     * Publishes a file upload event with S3 location.
     *
     * @param email User email
     * @param fileUuid File UUID
     * @param s3DataKey S3 key for encrypted data
     * @param s3MetadataKey S3 key for metadata JSON (contains all file details)
     * @param bucketName S3 bucket name
     */
    public void publishFileUpload(String email, String fileUuid,
                                   String s3DataKey, String s3MetadataKey,
                                   String bucketName) {
        FileUploadEvent event = new FileUploadEvent(
            email, fileUuid, s3DataKey, s3MetadataKey, bucketName
        );

        publishFileUploadEvent(event);
    }
}
