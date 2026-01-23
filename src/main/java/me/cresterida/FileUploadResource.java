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
import java.util.UUID;

@Path("/api")
public class FileUploadResource {

    @Inject
    S3Client s3;

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadFile(@RestForm("file") FileUpload file, @RestForm("email") String email) {
        String bucketName = "your-bucket-name";
        String key = "uploads/" + email + "/" + UUID.randomUUID() + "-" + file.fileName();

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        s3.putObject(putObjectRequest, RequestBody.fromFile(file.uploadedFile()));

        return Response.ok().build();
    }

    @POST
    @Path("/validate-passphrase")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response validatePassphrase(PassphraseRequest request) {
        if ("your-secret-passphrase".equals(request.passphrase)) {
            return Response.ok(new PassphraseResponse(true)).build();
        } else {
            return Response.ok(new PassphraseResponse(false)).build();
        }
    }

    public static class PassphraseRequest {
        public String passphrase;
    }

    public static class PassphraseResponse {
        public boolean validated;

        public PassphraseResponse(boolean validated) {
            this.validated = validated;
        }
    }
}
