package me.cresterida;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import me.cresterida.dto.ErrorResponse;
import me.cresterida.model.EnvelopeMetadata;
import me.cresterida.service.CryptoService;
import me.cresterida.util.S3StorageService;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Path("/api/decrypt")
@Tag(name = "Decrypt API", description = "File decryption and download endpoints")
public class DecryptResource {

    private static final Logger log = LoggerFactory.getLogger(DecryptResource.class);

    @Inject
    S3Client s3;

    @Inject
    CryptoService cryptoService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    S3StorageService s3StorageService;

    @ConfigProperty(name = "decrypt.download.path", defaultValue = "./downloads")
    String downloadPath;

    @ConfigProperty(name = "quarkus.profile")
    String profile;

    @GET
    @Operation(summary = "Decrypt and download file",
               description = "Decrypt a file from S3 and download it. In dev mode, also saves to local folder.")
    @APIResponse(responseCode = "200", description = "File decrypted successfully")
    @APIResponse(responseCode = "400", description = "Invalid request parameters")
    @APIResponse(responseCode = "404", description = "File not found")
    @APIResponse(responseCode = "500", description = "Decryption error")
    public Response decryptFile(
            @QueryParam("email") String email,
            @QueryParam("uuid") String uuid) {

        // Validate inputs
        if (email == null || email.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Email is required"))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        if (uuid == null || uuid.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("UUID is required"))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        log.info("=".repeat(80));
        log.info("Decrypting file for email: {}, UUID: {}", email, uuid);
        log.info("=".repeat(80));

        try {
            // Step 1: Construct metadata key - it's always at the same location
            // Pattern: uploads/{email}/{uuid}/metadata.json
            String metadataKey = "uploads/" + email + "/" + uuid + "/metadata.json";

            log.info("Fetching metadata from: {}", metadataKey);

            // Step 2: Download metadata from S3
            GetObjectRequest metadataRequest = GetObjectRequest.builder()
                    .bucket(s3StorageService.getBucketName())
                    .key(metadataKey)
                    .build();

            EnvelopeMetadata metadata;
            try (ResponseInputStream<GetObjectResponse> metadataStream = s3.getObject(metadataRequest)) {
                metadata = objectMapper.readValue(metadataStream, EnvelopeMetadata.class);
                log.info("✓ Metadata retrieved");
                log.info("  Original filename: {}", metadata.originalFilename);
                log.info("  Original size: {} bytes", metadata.originalSize);
            }

            // Step 3: Construct the actual data key using the original filename from metadata
            // Pattern: uploads/{email}/{uuid}/{filename}.enc
            String baseFilename = metadata.originalFilename.replace(".encrypted", "");
            String dataKey = "uploads/" + email + "/" + uuid + "/" + baseFilename + ".enc";

            log.info("Fetching encrypted data from: {}", dataKey);

            // Step 4: Download encrypted file from S3
            GetObjectRequest dataRequest = GetObjectRequest.builder()
                    .bucket(s3StorageService.getBucketName())
                    .key(dataKey)
                    .build();

            byte[] encryptedData;
            try (ResponseInputStream<GetObjectResponse> dataStream = s3.getObject(dataRequest)) {
                encryptedData = dataStream.readAllBytes();
                log.info("✓ Encrypted data retrieved: {} bytes", encryptedData.length);
            }

            // Step 5: Decrypt the data
            log.info("Decrypting with envelope KEK...");
            byte[] decryptedData = cryptoService.decryptWithKek(encryptedData, metadata.kek);
            log.info("✓ Decryption successful! Size: {} bytes", decryptedData.length);

            // Step 6: In dev mode, save to local folder
            if ("dev".equals(profile)) {
                saveToLocalFolder(email, uuid, metadata.originalFilename, decryptedData);
            }

            // Step 7: Return the decrypted file as download
            String cleanFilename = metadata.originalFilename.replace(".encrypted", "");

            StreamingOutput stream = output -> {
                output.write(decryptedData);
                output.flush();
            };

            log.info("=".repeat(80));
            return Response.ok(stream)
                    .header("Content-Disposition", "attachment; filename=\"" + cleanFilename + "\"")
                    .header("Content-Type", "application/octet-stream")
                    .header("Content-Length", decryptedData.length)
                    .build();

        } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException e) {
            log.error("✗ File not found in S3: {}", e.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("File not found: " + e.getMessage()))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        } catch (Exception e) {
            log.error("✗ Decryption error: {}", e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Decryption failed: " + e.getMessage()))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    /**
     * Save decrypted file to local folder (dev mode only)
     */
    private void saveToLocalFolder(String email, String uuid, String originalFilename, byte[] data) {
        try {
            // Create download directory structure: downloads/{email}/{uuid}/
            java.nio.file.Path downloadDir = Paths.get(downloadPath, email, uuid);
            Files.createDirectories(downloadDir);

            // Save the file
            String cleanFilename = originalFilename.replace(".encrypted", "");
            java.nio.file.Path filePath = downloadDir.resolve(cleanFilename);

            Files.write(filePath, data);

            log.info("✓ File saved locally: {}", filePath.toAbsolutePath());
        } catch (IOException e) {
            log.warn("⚠ Warning: Failed to save file locally: {}", e.getMessage());
            // Don't throw exception - local save is optional
        }
    }

    @GET
    @Path("/metadata")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get file metadata",
               description = "Retrieve metadata for an encrypted file from S3")
    @APIResponse(responseCode = "200", description = "Metadata retrieved successfully")
    @APIResponse(responseCode = "400", description = "Invalid request parameters")
    @APIResponse(responseCode = "404", description = "Metadata not found")
    @APIResponse(responseCode = "500", description = "Metadata retrieval error")
    public Response obtainMetadata(@QueryParam("email") String email, @QueryParam("uuid") String uuid ) {

        // Validate inputs
        if (email == null || email.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Email is required"))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        if (uuid == null || uuid.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("UUID is required"))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        log.info("=".repeat(80));
        log.info("Fetching metadata for email: {}, UUID: {}", email, uuid);
        log.info("=".repeat(80));

        try {
            String metadataKey = "uploads/" + email + "/" + uuid + "/metadata.json";
            log.info("Fetching metadata from: {}", metadataKey);

            // Download metadata from S3
            GetObjectRequest metadataRequest = GetObjectRequest.builder()
                    .bucket(s3StorageService.getBucketName())
                    .key(metadataKey)
                    .build();

            EnvelopeMetadata metadata;

            try (ResponseInputStream<GetObjectResponse> metadataStream = s3.getObject(metadataRequest)) {
                metadata = objectMapper.readValue(metadataStream, EnvelopeMetadata.class);
                log.info("✓ Metadata retrieved");
                log.info("  Original filename: {}", metadata.originalFilename);
                log.info("  Original size: {} bytes", metadata.originalSize);
                log.info("=".repeat(80));

                return Response.ok(metadata)
                        .type(MediaType.APPLICATION_JSON)
                        .build();
            }

        } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException e) {
            log.error("✗ Metadata not found in S3: {}", e.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Metadata not found: " + e.getMessage()))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        } catch (Exception e) {
            log.error("✗ Metadata retrieval error: {}", e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Metadata retrieval failed: " + e.getMessage()))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

    }
}
