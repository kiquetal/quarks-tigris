package me.cresterida;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import me.cresterida.dto.DeleteFileResponse;
import me.cresterida.model.EnvelopeMetadata;
import me.cresterida.service.SessionManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Path("/api/files")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "File Management", description = "Endpoints for listing and managing uploaded files")
public class FileListResource {

    private static final Logger logger = LoggerFactory.getLogger(FileListResource.class);

    @Inject
    SessionManager sessionManager;

    @Inject
    S3Client s3Client;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "bucket.name")
    String bucketName;

    /**
     * List all files for authenticated user
     * Requires valid session token in X-Session-Token header
     */
    @GET
    @Operation(summary = "List files", description = "List all files uploaded by the authenticated user (requires valid session)")
    public Response listFiles(@HeaderParam("X-Session-Token") String sessionToken) {
        System.out.println("\n📋 List files request received");
        System.out.println("   Session token: " + (sessionToken != null ? sessionToken.substring(0, Math.min(8, sessionToken.length())) + "..." : "null"));

        // Validate session
        String email = sessionManager.validateSession(sessionToken);
        if (email == null) {
            System.out.println("   ❌ Invalid or expired session");
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("error", "Invalid or expired session. Please login again."))
                .build();
        }

        System.out.println("   ✅ Session valid for email: " + email);

        try {
            // List all metadata files for this user
            List<EnvelopeMetadata> metadataList = fetchUserMetadata(email);

            System.out.println("   📦 Retrieved " + metadataList.size() + " files for user: " + email);
            return Response.ok(metadataList).build();

        } catch (Exception e) {
            logger.error("Error listing files for user: {}", email, e);
            System.err.println("   ❌ Error listing files: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Failed to retrieve files"))
                .build();
        }
    }

    /**
     * Fetch all metadata.json files for a user from S3
     */
    private List<EnvelopeMetadata> fetchUserMetadata(String email) {
        List<EnvelopeMetadata> metadataList = new ArrayList<>();

        try {
            // List objects with prefix (user's folder in S3)
            String prefix = "uploads/" + email + "/";
            System.out.println("   🔍 Searching S3 bucket: " + bucketName + " with prefix: " + prefix);

            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build();

            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);
            System.out.println("   📂 Found " + listResponse.contents().size() + " objects in S3");

            // Filter metadata.json files
            for (S3Object s3Object : listResponse.contents()) {
                if (s3Object.key().endsWith("/metadata.json")) {
                    System.out.println("   📄 Processing metadata: " + s3Object.key());
                    EnvelopeMetadata metadata = downloadMetadata(s3Object.key());
                    if (metadata != null) {
                        // Extract fileId from S3 key: uploads/email/fileId/metadata.json
                        String[] keyParts = s3Object.key().split("/");
                        if (keyParts.length >= 3) {
                            String fileId = keyParts[2]; // The UUID folder
                            metadata.fileId = fileId;
                            System.out.println("      📋 File ID: " + fileId);
                        }

                        metadataList.add(metadata);
                        System.out.println("      ✓ Added file: " + metadata.originalFilename);
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error fetching metadata from S3", e);
            System.err.println("   ❌ Error fetching from S3: " + e.getMessage());
        }

        return metadataList;
    }

    /**
     * Download and parse metadata.json from S3
     */
    private EnvelopeMetadata downloadMetadata(String s3Key) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

            try (ResponseInputStream<GetObjectResponse> inputStream = s3Client.getObject(getRequest)) {
                String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                // Parse JSON to EnvelopeMetadata
                EnvelopeMetadata metadata = objectMapper.readValue(json, EnvelopeMetadata.class);
                return metadata;
            }

        } catch (Exception e) {
            logger.error("Error downloading metadata: {}", s3Key, e);
            System.err.println("      ⚠ Failed to download/parse metadata: " + e.getMessage());
            return null;
        }
    }

    /**
     * Delete a file for authenticated user
     * Requires valid session token in X-Session-Token header
     */
    @DELETE
    @Operation(summary = "Delete file", description = "Delete a specific file from S3 storage for the authenticated user")
    @APIResponse(responseCode = "200", description = "File deleted successfully",
        content = @Content(schema = @Schema(implementation = DeleteFileResponse.class)))
    @APIResponse(responseCode = "401", description = "Unauthorized - Invalid or expired session")
    @APIResponse(responseCode = "500", description = "Internal server error")
    public Response deleteFile(
            @HeaderParam("X-Session-Token") String sessionToken,
            @QueryParam("fileId") String fileId,
            @QueryParam("fileName") String fileName) {

        System.out.println("\n🗑️ Delete file request received");
        System.out.println("   File ID: " + fileId);
        System.out.println("   File Name: " + fileName);

        // Validate required parameters
        if (fileId == null || fileId.isEmpty()) {
            System.out.println("   ❌ Missing fileId parameter");
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "fileId parameter is required"))
                .build();
        }

        if (fileName == null || fileName.isEmpty()) {
            System.out.println("   ❌ Missing fileName parameter");
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "fileName parameter is required"))
                .build();
        }

        // Validate session token
        if (sessionToken == null || sessionToken.isEmpty()) {
            System.out.println("   ❌ No session token provided");
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("error", "Session token required"))
                .build();
        }

        String email = sessionManager.validateSession(sessionToken);
        if (email == null) {
            System.out.println("   ❌ Invalid or expired session");
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("error", "Invalid or expired session"))
                .build();
        }

        System.out.println("   ✅ Session valid for email: " + email);

        // Generate S3 key
        String s3Key = generateS3Key(email, fileId, fileName);

        try {
            System.out.println("   🔄 Deleting file: " + s3Key);

            // Delete the encrypted file
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            s3Client.deleteObject(deleteRequest);

            // Also delete the metadata file
            String metadataKey = "uploads/" + email + "/" + fileId + "/metadata.json";
            DeleteObjectRequest metadataDeleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(metadataKey)
                    .build();

            s3Client.deleteObject(metadataDeleteRequest);

            System.out.println("   ✅ File deleted successfully");

            // Create and return response DTO
            DeleteFileResponse response = new DeleteFileResponse(
                "File deleted successfully",
                fileId,
                fileName,
                true,
                s3Key
            );

            return Response.ok(response).build();

        } catch (S3Exception e) {
            logger.error("S3 error deleting file: {}", s3Key, e);
            System.err.println("   ❌ S3 error: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Failed to delete file from storage"))
                .build();

        } catch (Exception e) {
            logger.error("Error deleting file: {}", s3Key, e);
            System.err.println("   ❌ Error: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Failed to delete file"))
                .build();
        }
    }

    private String generateS3Key(String email, String fileId, String objectName) {

        return "uploads/" + email + "/" + fileId + "/" + objectName;

    }

}
