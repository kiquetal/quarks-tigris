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
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Path("/api")
@Tag(name = "Whisper API", description = "MP3 file upload and authentication endpoints")
public class FileUploadResource {

    @Inject
    S3Client s3;

    @Inject
    CryptoService cryptoService;

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
            @RestForm("email") String email,
            @RestForm("passphrase") String passphrase) {

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
        System.out.println("Passphrase provided: " + (passphrase != null && !passphrase.isEmpty()));
        System.out.println("=".repeat(80));

        try {
            // Read the encrypted file from Angular
            byte[] encryptedData = Files.readAllBytes(file.uploadedFile());
            System.out.println("Read encrypted file: " + encryptedData.length + " bytes");

            // Verify encryption and apply envelope encryption
            CryptoService.EncryptionResult result = cryptoService.verifyAndEnvelopeEncrypt(
                encryptedData,
                passphrase,
                file.fileName()
            );

            System.out.println("Encryption verification: " + (result.verified ? "SUCCESS" : "SKIPPED"));
            System.out.println("Envelope encrypted size: " + result.envelopeEncryptedData.length + " bytes");
            System.out.println("Original decrypted size: " + result.originalSize + " bytes");

            // Generate S3 key with metadata
            String baseFileName = file.fileName().replace(".encrypted", "");
            String key = "uploads/" + email + "/" + UUID.randomUUID() + "-" + baseFileName + ".enc";
            String metadataKey = key + ".metadata";

            // Store metadata separately (encrypted data key, original size, etc.)
            Map<String, String> metadata = new HashMap<>();
            metadata.put("encrypted-data-key", result.encryptedDataKey);
            metadata.put("original-filename", result.originalFileName);
            metadata.put("original-size", String.valueOf(result.originalSize));
            metadata.put("verified", String.valueOf(result.verified));
            metadata.put("encryption-version", "2.0-envelope");

            // Upload envelope-encrypted file to S3
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .metadata(metadata)
                    .contentType("application/octet-stream")
                    .build();

            s3.putObject(putObjectRequest, RequestBody.fromBytes(result.envelopeEncryptedData));
            System.out.println("File uploaded to S3: " + key);
            System.out.println("=".repeat(80));

            return Response.ok(new UploadResponse(
                "File uploaded successfully with envelope encryption",
                key,
                result.verified,
                result.originalSize
            )).build();

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
