package me.cresterida;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.RestForm;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

@Path("/api")
@Tag(name = "Whisper API", description = "MP3 file upload and authentication endpoints")
public class FileUploadResource {

    @Inject
    S3Client s3;

    @Inject
    CryptoService cryptoService;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "bucket.name")
    String bucketName;

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
        System.out.println("=".repeat(80));
        System.out.println("Uploading encrypted file: " + file.fileName() + " (" + file.size() + " bytes)");
        System.out.println("Email: " + email);
        System.out.println("=".repeat(80));

        try {
            // Generate S3 keys first
            String baseFileName = file.fileName().replace(".encrypted", "");
            String fileId = UUID.randomUUID().toString();
            String dataKey = "uploads/" + email + "/" + fileId + "/" + baseFileName + ".enc";
            String metadataKey = "uploads/" + email + "/" + fileId + "/metadata.json";

            // Create temporary file for decrypted data (will be cleaned up automatically)
            java.nio.file.Path tempDecrypted = Files.createTempFile("decrypted-", ".tmp");
            java.nio.file.Path tempEncrypted = Files.createTempFile("kek-encrypted-", ".tmp");

            try {
                // Step 1: Verify and decrypt the data from Angular (streaming)
                try (java.io.FileInputStream encryptedInput = new java.io.FileInputStream(file.uploadedFile().toFile());
                     java.io.FileOutputStream decryptedOutput = new java.io.FileOutputStream(tempDecrypted.toFile())) {

                    CryptoService.StreamingDecryptionResult decryptResult =
                        cryptoService.verifyAndDecryptStreaming(encryptedInput, decryptedOutput);

                    System.out.println("Decryption verification: " + (decryptResult.verified ? "SUCCESS" : "FAILED"));
                    System.out.println("Decrypted size: " + decryptResult.size + " bytes");

                    // Step 2: Encrypt the decrypted data using DEK (streaming)
                    try (java.io.FileInputStream decryptedInput = new java.io.FileInputStream(tempDecrypted.toFile());
                         java.io.FileOutputStream dekEncryptedOutput = new java.io.FileOutputStream(tempEncrypted.toFile())) {

                        CryptoService.StreamingDataEncryptionResult dekResult =
                            cryptoService.encryptWithDekStreaming(decryptedInput, dekEncryptedOutput);

                        System.out.println("DEK encrypted size: " + dekResult.encryptedSize + " bytes");

                        // Step 3: Create envelope by encrypting the DEK with master key
                        String encryptedDek = cryptoService.createEnvelopeDek(dekResult.dek);
                        System.out.println("Envelope created: DEK encrypted with master key");

                        // Clear the plaintext DEK from memory
                        java.util.Arrays.fill(dekResult.dek, (byte) 0);

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
                        System.out.println("Created envelope metadata JSON:");
                        System.out.println(metadataJson);

                        // Upload DEK-encrypted data to S3 (streaming)
                        PutObjectRequest dataRequest = PutObjectRequest.builder()
                                .bucket(bucketName)
                                .key(dataKey)
                                .contentType("application/octet-stream")
                                .contentLength(dekResult.encryptedSize)
                                .build();
                        s3.putObject(dataRequest, RequestBody.fromFile(tempEncrypted));
                        System.out.println("✓ DEK-encrypted data uploaded to S3: " + dataKey);

                        // Upload metadata JSON to S3
                        PutObjectRequest metadataRequest = PutObjectRequest.builder()
                                .bucket(bucketName)
                                .key(metadataKey)
                                .contentType("application/json")
                                .build();
                        s3.putObject(metadataRequest, RequestBody.fromString(metadataJson));
                        System.out.println("✓ Envelope metadata uploaded to S3: " + metadataKey);
                        System.out.println("=".repeat(80));

                        return Response.ok(new UploadResponse(
                            "File uploaded successfully with envelope encryption",
                            dataKey,
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
                    System.err.println("Warning: Failed to delete temporary files: " + e.getMessage());
                }
            }

        } catch (IOException e) {
            System.err.println("IO Error: " + e.getMessage());
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to read file: " + e.getMessage()))
                    .build();
        } catch (Exception e) {
            System.err.println("Encryption Error: " + e.getMessage());
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to process encrypted file: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/validate-passphrase")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Validate passphrase", description = "Validate the user's passphrase to allow access to upload functionality")
    @APIResponse(responseCode = "200", description = "Passphrase validation result", content = @Content(schema = @Schema(implementation = PassphraseResponse.class)))
    public Response validatePassphrase(PassphraseRequest request) {
        if ("your-secret-passphrase".equals(request.passphrase)) {
            return Response.ok(new PassphraseResponse(true)).build();
        } else {
            return Response.status(Response.Status.FORBIDDEN).
                    entity(new PassphraseResponse(false)).build();
        }
    }

    @Schema(description = "Passphrase validation request")
    public static class PassphraseRequest {
        @Schema(description = "The passphrase to validate", required = true)
        public String passphrase;
    }

    @Schema(description = "Passphrase validation response")
    public static class PassphraseResponse {
        @Schema(description = "Whether the passphrase is valid")
        public boolean validated;

        public PassphraseResponse(boolean validated) {
            this.validated = validated;
        }
    }

    @Schema(description = "File upload response")
    public static class UploadResponse {
        @Schema(description = "Status message")
        public String message;
        @Schema(description = "S3 object key")
        public String key;
        @Schema(description = "Whether encryption was verified")
        public boolean verified;
        @Schema(description = "Original file size (before encryption)")
        public long originalSize;

        public UploadResponse(String message, String key, boolean verified, long originalSize) {
            this.message = message;
            this.key = key;
            this.verified = verified;
            this.originalSize = originalSize;
        }
    }

    @Schema(description = "Error response")
    public static class ErrorResponse {
        @Schema(description = "Error message")
        public String message;

        public ErrorResponse(String message) {
            this.message = message;
        }
    }
}
