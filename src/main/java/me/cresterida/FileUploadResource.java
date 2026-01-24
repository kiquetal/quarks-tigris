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
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.RestForm;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.util.UUID;

@Path("/api")
@Tag(name = "Whisper API", description = "MP3 file upload and authentication endpoints")
public class FileUploadResource {

    @Inject
    S3Client s3;

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Upload MP3 file", description = "Upload an MP3 file to S3 storage with associated email")
    @APIResponse(responseCode = "200", description = "File uploaded successfully")
    @APIResponse(responseCode = "400", description = "Invalid file or missing email")
    public Response uploadFile(@RestForm("file") FileUpload file, @RestForm("email") String email) {
        String bucketName = "your-bucket-name";
        String key = "uploads/" + email + "/" + UUID.randomUUID() + "-" + file.fileName();

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        s3.putObject(putObjectRequest, RequestBody.fromFile(file.uploadedFile()));

        return Response.ok(new UploadResponse("File uploaded successfully", key)).build();
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
            return Response.ok(new PassphraseResponse(false)).build();
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

        public UploadResponse(String message, String key) {
            this.message = message;
            this.key = key;
        }
    }
}
