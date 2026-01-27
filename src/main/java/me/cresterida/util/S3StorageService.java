package me.cresterida.util;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Path;

/**
 * Utility class for S3 storage operations.
 * Handles uploading files and metadata to S3/Tigris storage.
 */
@ApplicationScoped
public class S3StorageService
{

    private Logger logger = LoggerFactory.getLogger(S3StorageService.class);
    @Inject
    S3Client s3;

    @ConfigProperty(name = "bucket.name")
    String bucketName;

    /**
     * Upload result containing the S3 keys for uploaded objects.
     */
    public static class UploadResult
    {
        public String dataKey;
        public String metadataKey;

        public UploadResult(String dataKey, String metadataKey)
        {
            this.dataKey = dataKey;
            this.metadataKey = metadataKey;
        }
    }

    /**
     * Generate S3 keys for file upload.
     *
     * @param email    User email
     * @param fileName Original file name
     * @param fileId   Unique file identifier
     * @return UploadResult with generated keys
     */
    public UploadResult generateKeys(String email, String fileName, String fileId)
    {
        String baseFileName = fileName.replace(".encrypted", "");
        String dataKey = "uploads/" + email + "/" + fileId + "/" + baseFileName + ".enc";
        String metadataKey = "uploads/" + email + "/" + fileId + "/metadata.json";

        return new UploadResult(dataKey, metadataKey);
    }

    /**
     * Upload encrypted file data to S3.
     *
     * @param key      S3 object key
     * @param filePath Path to the file to upload
     * @param fileSize Size of the file in bytes
     */
    public void uploadEncryptedFile(String key, Path filePath, long fileSize)
    {
        System.out.println("Uploading encrypted data to S3...");

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType("application/octet-stream")
                .contentLength(fileSize)
                .build();

        s3.putObject(request, RequestBody.fromFile(filePath));

        System.out.println("✓ DEK-encrypted data uploaded to S3: " + key);
    }

    /**
     * Upload metadata JSON to S3.
     *
     * @param key          S3 object key
     * @param metadataJson JSON content as string
     */
    public void uploadMetadata(String key, String metadataJson)
    {
        System.out.println("Uploading metadata to S3...");

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType("application/json")
                .build();

        s3.putObject(request, RequestBody.fromString(metadataJson));

        System.out.println("✓ Envelope metadata uploaded to S3: " + key);
    }

    /**
     * Upload both encrypted file and metadata to S3.
     *
     * @param email             User email
     * @param fileName          Original file name
     * @param fileId            Unique file identifier
     * @param encryptedFilePath Path to encrypted file
     * @param encryptedFileSize Size of encrypted file
     * @param metadataJson      Metadata JSON content
     * @return UploadResult with the S3 keys used
     */
    public UploadResult uploadFileAndMetadata(String email, String fileName, String fileId,
                                              Path encryptedFilePath, long encryptedFileSize,
                                              String metadataJson)
    {
        UploadResult result = generateKeys(email, fileName, fileId);

        uploadEncryptedFile(result.dataKey, encryptedFilePath, encryptedFileSize);
        uploadMetadata(result.metadataKey, metadataJson);

        return result;
    }

    /**
     * Get the configured bucket name.
     *
     * @return S3 bucket name
     */
    public String getBucketName()
    {
        return bucketName;
    }

    /**
     * Download a file from S3.
     *
     * @param key S3 object key
     * @return File content as byte array
     */
    public byte[] downloadFile(String key)
    {
        System.out.println("Downloading file from S3: " + key);

        try (var response = s3.getObject(builder -> builder
                .bucket(bucketName)
                .key(key))) {
            return response.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to download file from S3: " + e.getMessage(), e);
        }
    }

    /**
     * Download metadata JSON from S3.
     *
     * @param key S3 object key
     * @return Metadata JSON content as string
     */
    public String downloadMetadata(String key)
    {
        System.out.println("Downloading metadata from S3: " + key);

        try (var response = s3.getObject(builder -> builder
                .bucket(bucketName)
                .key(key))) {
            return new String(response.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Failed to download metadata from S3: " + e.getMessage(), e);
        }
    }

    /**
     *  Download a specific object from S3
      * @param s3Key S3 object key
     *  @return GetObjectResponse
     */

    public GetObjectResponse downloadFileFromBucket(String s3Key)
    {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();
            return s3.getObject(request).response();
        } catch (Exception e) {
            logger.error("Failed to download file from S3: " + e.getMessage(), e);
            throw new RuntimeException("Failed to download file from S3: " + e.getMessage(), e);

        }
    }
}
