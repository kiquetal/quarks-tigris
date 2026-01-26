package me.cresterida;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.inject.Inject;
import me.cresterida.service.NatsService;
import me.cresterida.service.SessionManager;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.RestForm;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.cresterida.dto.ErrorResponse;
import me.cresterida.dto.PassphraseRequest;
import me.cresterida.dto.PassphraseResponse;
import me.cresterida.dto.UploadResponse;
import me.cresterida.model.EnvelopeMetadata;
import me.cresterida.service.CryptoService;
import me.cresterida.util.S3StorageService;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.UUID;

@Path("/api")
@Tag(name = "Whisper API", description = "MP3 file upload and authentication endpoints")
public class FileUploadResource {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadResource.class);

    @Inject
    S3StorageService s3StorageService;

    @Inject
    CryptoService cryptoService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    NatsService natsService;


    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Upload MP3 file", description = "Upload an encrypted MP3 file to S3 storage with envelope encryption")
    @APIResponse(responseCode = "200", description = "File uploaded successfully")
    @APIResponse(responseCode = "400", description = "Invalid file or missing email")
    @APIResponse(responseCode = "413", description = "File too large")
    public Response uploadFile(
            @RestForm("file") FileUpload file,
            @RestForm("email") String email) {

        // Validate inputs
        if (file == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("No file provided"))
                    .build();
        }

        if (email == null || email.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Email is required"))
                    .build();
        }

        // Check file size (100MB limit for encrypted file)
        long maxFileSize = 100 * 1024 * 1024; // 100MB in bytes
        if (file.size() > maxFileSize) {
            return Response.status(413)
                    .entity(new ErrorResponse("File is too large. Maximum size is 100MB."))
                    .build();
        }

        // Log file info
        logger.info("=".repeat(80));
        logger.info("Uploading encrypted file: {} ({} bytes)", file.fileName(), file.size());
        logger.info("Email: {}", email);
        logger.info("=".repeat(80));

        try {
            // Generate S3 keys first
            String fileId = UUID.randomUUID().toString();

            // Create temporary file for decrypted data (will be cleaned up automatically)
            java.nio.file.Path tempDecrypted = Files.createTempFile("decrypted-", ".tmp");
            java.nio.file.Path tempEncrypted = Files.createTempFile("kek-encrypted-", ".tmp");

            try {
                // Step 1: Verify and decrypt the data from Angular (streaming)
                try (java.io.FileInputStream encryptedInput = new java.io.FileInputStream(file.uploadedFile().toFile());
                     java.io.FileOutputStream decryptedOutput = new java.io.FileOutputStream(tempDecrypted.toFile())) {

                    CryptoService.StreamingDecryptionResult decryptResult =
                        cryptoService.verifyAndDecryptStreaming(encryptedInput, decryptedOutput);

                    logger.info("Decryption verification: {}", decryptResult.verified ? "SUCCESS" : "FAILED");
                    logger.debug("Decrypted size: {} bytes", decryptResult.size);

                    // Step 2: Encrypt the decrypted data using DEK (streaming)
                    try (FileInputStream decryptedInput = new FileInputStream(tempDecrypted.toFile());
                         FileOutputStream dekEncryptedOutput = new FileOutputStream(tempEncrypted.toFile())) {

                        CryptoService.StreamingDataEncryptionResult dekResult =
                            cryptoService.encryptWithDekStreaming(decryptedInput, dekEncryptedOutput);

                        logger.debug("DEK encrypted size: {} bytes", dekResult.encryptedSize);

                        // Step 3: Create envelope by encrypting the DEK with master key
                        String encryptedDek = cryptoService.createEnvelopeDek(dekResult.dek);
                        logger.debug("Envelope created: DEK encrypted with master key");

                        // Clear the plaintext DEK from memory
                        Arrays.fill(dekResult.dek, (byte) 0);

                        // Create envelope metadata (with the encrypted DEK)
                        EnvelopeMetadata metadata = new EnvelopeMetadata(
                            encryptedDek,  // The DEK encrypted with master key
                            file.fileName(),
                            decryptResult.size,
                            dekResult.encryptedSize,
                            decryptResult.verified
                        );

                        // Serialize metadata to JSON
                        String metadataJson = objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(metadata);
                        logger.debug("Created envelope metadata JSON:\n{}", metadataJson);

                        // Upload encrypted file and metadata to S3
                        S3StorageService.UploadResult uploadResult = s3StorageService.uploadFileAndMetadata(
                            email,
                            file.fileName(),
                            fileId,
                            tempEncrypted,
                            dekResult.encryptedSize,
                            metadataJson
                        );

                        // Publish event to NATS for downstream processing
                        // Only S3 location - consumer downloads full metadata.json
                        publishToNats(email, fileId, uploadResult);

                        logger.info("=".repeat(80));

                        return Response.ok(new UploadResponse(
                            "File uploaded successfully with envelope encryption",
                            uploadResult.dataKey,
                            decryptResult.verified,
                            decryptResult.size
                        )).build();
                    }
                }
            } finally {
                // Clean up temporary files
                try {
                    Files.deleteIfExists(tempDecrypted);
                    Files.deleteIfExists(tempEncrypted);
                } catch (IOException e) {
                    logger.warn("Failed to delete temporary files: {}", e.getMessage());
                }
            }

        } catch (IOException e) {
            logger.error("IO Error: {}", e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to read file: " + e.getMessage()))
                    .build();
        } catch (Exception e) {
            logger.error("Encryption Error: {}", e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to process encrypted file: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Publishes file upload event to NATS for downstream processing.
     * Only publishes S3 location (data key, metadata key, bucket).
     * Consumer can download the full metadata.json from S3 to get all file details.
     *
     * @param email User email
     * @param fileId File UUID
     * @param uploadResult S3 upload result with keys
     */
    private void publishToNats(String email, String fileId,
                               S3StorageService.UploadResult uploadResult) {
        try {
            natsService.publishFileUpload(
                email,
                fileId,
                uploadResult.dataKey,
                uploadResult.metadataKey,
                s3StorageService.getBucketName()
            );
        } catch (Exception e) {
            // Log but don't fail upload if NATS publish fails
            logger.warn("Failed to publish to NATS: {}", e.getMessage(), e);
        }
    }

    @Inject
    SessionManager sessionManager;

    @POST
    @Path("/validate-passphrase")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Validate passphrase", description = "Validate the user's passphrase to allow access to upload functionality")
    @APIResponse(responseCode = "200", description = "Passphrase validation result", content = @Content(schema = @Schema(implementation = PassphraseResponse.class)))
    public Response validatePassphrase(PassphraseRequest request) {
        logger.debug("Validating passphrase...");

        // Validate passphrase and get email
        String email = sessionManager.validatePassphrase(request.passphrase);

        if (email != null) {
            // Create session
            String sessionToken = sessionManager.createSession(email);

            logger.info("Passphrase valid for email: {}", email);
            logger.debug("Session token: {}...", sessionToken.substring(0, 8));

            PassphraseResponse response = new PassphraseResponse(true);
            response.sessionToken = sessionToken;
            response.email = email;

            return Response.ok(response).build();
        } else {
            logger.warn("Invalid passphrase attempt");
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new PassphraseResponse(false)).build();
        }
    }
}
