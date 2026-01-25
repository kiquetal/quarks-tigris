package me.cresterida.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.cresterida.dto.FileUploadEvent;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.util.concurrent.CompletionStage;

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
