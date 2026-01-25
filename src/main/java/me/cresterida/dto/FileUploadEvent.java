package me.cresterida.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Metadata event published to NATS when a file is uploaded.
 * Consumed by downstream services (e.g., F# processing service) to download and process files.
 */
public class FileUploadEvent {

    @JsonProperty("event_id")
    public String eventId;

    @JsonProperty("email")
    public String email;

    @JsonProperty("file_uuid")
    public String fileUuid;

    @JsonProperty("original_filename")
    public String originalFilename;

    @JsonProperty("original_size")
    public long originalSize;

    @JsonProperty("encrypted_size")
    public long encryptedSize;

    @JsonProperty("s3_data_key")
    public String s3DataKey;

    @JsonProperty("s3_metadata_key")
    public String s3MetadataKey;

    @JsonProperty("bucket_name")
    public String bucketName;

    @JsonProperty("verification_status")
    public String verificationStatus;

    @JsonProperty("upload_timestamp")
    public long uploadTimestamp;

    @JsonProperty("content_type")
    public String contentType;

    public FileUploadEvent() {
        this.uploadTimestamp = System.currentTimeMillis();
    }

    public FileUploadEvent(String email, String fileUuid, String originalFilename,
                          long originalSize, long encryptedSize,
                          String s3DataKey, String s3MetadataKey, String bucketName,
                          boolean verified) {
        this();
        this.eventId = java.util.UUID.randomUUID().toString();
        this.email = email;
        this.fileUuid = fileUuid;
        this.originalFilename = originalFilename;
        this.originalSize = originalSize;
        this.encryptedSize = encryptedSize;
        this.s3DataKey = s3DataKey;
        this.s3MetadataKey = s3MetadataKey;
        this.bucketName = bucketName;
        this.verificationStatus = verified ? "VERIFIED" : "NOT_VERIFIED";
        this.contentType = determineContentType(originalFilename);
    }

    private String determineContentType(String filename) {
        if (filename == null) return "application/octet-stream";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        return "application/octet-stream";
    }
}
