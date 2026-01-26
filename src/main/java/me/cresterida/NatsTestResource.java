package me.cresterida;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import me.cresterida.service.NatsService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Test endpoint for manually testing NATS message publishing.
 * Use this to verify NATS stream is working before uploading files.
 */
@Path("/api/nats-test")
@Tag(name = "NATS Testing", description = "Endpoints for testing NATS JetStream integration")
public class NatsTestResource {

    private static final Logger logger = LoggerFactory.getLogger(NatsTestResource.class);

    @Inject
    NatsService natsService;

    /**
     * Simple GET endpoint to publish a test message to NATS.
     *
     * Usage: GET /api/nats-test/publish
     */
    @GET
    @Path("/publish")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Publish test message to NATS",
               description = "Sends a simple test message to file.uploads subject")
    public Response publishTestMessage() {
        try {
            String testEmail = "test@example.com";
            String testFileUuid = UUID.randomUUID().toString();
            String testDataKey = "test/data/" + testFileUuid + "/test.enc";
            String testMetadataKey = "test/data/" + testFileUuid + "/metadata.json";
            String testBucket = "whisper-uploads";

            logger.info("=".repeat(80));
            logger.info("Manual NATS Test - Publishing test message");
            logger.info("=".repeat(80));

            natsService.publishFileUpload(
                testEmail,
                testFileUuid,
                testDataKey,
                testMetadataKey,
                testBucket
            );

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Test message published to NATS");
            response.put("testEmail", testEmail);
            response.put("testFileUuid", testFileUuid);
            response.put("subject", "file.uploads");
            response.put("stream", "FILE_UPLOADS");
            response.put("note", "Check your NATS subscriber or run: nats stream view FILE_UPLOADS");

            return Response.ok(response).build();

        } catch (Exception e) {
            logger.error("Failed to publish test message: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Failed to publish test message");
            error.put("error", e.getMessage());

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(error)
                    .build();
        }
    }

    /**
     * POST endpoint to publish a custom test message to NATS.
     *
     * Usage: POST /api/nats-test/publish-custom?email=user@test.com&fileId=abc123
     */
    @POST
    @Path("/publish-custom")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Publish custom test message to NATS",
               description = "Sends a custom test message with specified email and fileId")
    public Response publishCustomMessage(
            @QueryParam("email") String email,
            @QueryParam("fileId") String fileId) {

        try {
            String testEmail = email != null ? email : "test@example.com";
            String testFileUuid = fileId != null ? fileId : UUID.randomUUID().toString();
            String testDataKey = "test/data/" + testFileUuid + "/test.enc";
            String testMetadataKey = "test/data/" + testFileUuid + "/metadata.json";
            String testBucket = "whisper-uploads";

            logger.info("=".repeat(80));
            logger.info("Manual NATS Test - Publishing custom message");
            logger.info("   Email: {}", testEmail);
            logger.info("   File ID: {}", testFileUuid);
            logger.info("=".repeat(80));

            natsService.publishFileUpload(
                testEmail,
                testFileUuid,
                testDataKey,
                testMetadataKey,
                testBucket
            );

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Custom test message published to NATS");
            response.put("email", testEmail);
            response.put("fileUuid", testFileUuid);
            response.put("dataKey", testDataKey);
            response.put("metadataKey", testMetadataKey);
            response.put("bucket", testBucket);
            response.put("subject", "file.uploads");
            response.put("stream", "FILE_UPLOADS");

            return Response.ok(response).build();

        } catch (Exception e) {
            logger.error("Failed to publish custom test message: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Failed to publish custom test message");
            error.put("error", e.getMessage());

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(error)
                    .build();
        }
    }

    /**
     * Health check endpoint to verify NATS configuration.
     */
    @GET
    @Path("/config")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get NATS configuration",
               description = "Returns current NATS configuration for debugging")
    public Response getNatsConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("channel", "file-uploads");
        config.put("subject", "file.uploads");
        config.put("stream", "FILE_UPLOADS");
        config.put("expectedPort", "32871");
        config.put("authentication", "guest:guest");
        config.put("createStreamCommand",
            "nats stream add FILE_UPLOADS --server nats://guest:guest@localhost:32871 --subjects \"file.uploads\" --storage file --retention limits --max-age 7d");
        config.put("subscribeCommand",
            "nats sub \"file.uploads\" --server nats://guest:guest@localhost:32871");
        config.put("viewStreamCommand",
            "nats stream view FILE_UPLOADS --server nats://guest:guest@localhost:32871");

        return Response.ok(config).build();
    }

    /**
     * Simple ping endpoint.
     */
    @GET
    @Path("/ping")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Ping test endpoint", description = "Simple health check")
    public String ping() {
        return "NATS test endpoint is alive! Use /api/nats-test/publish to send a test message.";
    }
}
