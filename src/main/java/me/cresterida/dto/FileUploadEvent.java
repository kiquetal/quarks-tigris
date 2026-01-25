package me.cresterida.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Simplified metadata event published to NATS when a file is uploaded.
 * Contains only the S3 location information needed by downstream services
 * to download the file and its metadata.
 */
public class FileUploadEvent {

    @JsonProperty("event_id")
    public String eventId;

    @JsonProperty("email")
    public String email;

    @JsonProperty("file_uuid")
    public String fileUuid;

    @JsonProperty("s3_data_key")
    public String s3DataKey;

    @JsonProperty("s3_metadata_key")
    public String s3MetadataKey;

    @JsonProperty("bucket_name")
    public String bucketName;

    @JsonProperty("timestamp")
    public long timestamp;

    public FileUploadEvent() {
        this.timestamp = System.currentTimeMillis();
    }

    public FileUploadEvent(String email, String fileUuid,
                          String s3DataKey, String s3MetadataKey,
                          String bucketName) {
        this();
        this.eventId = java.util.UUID.randomUUID().toString();
        this.email = email;
        this.fileUuid = fileUuid;
        this.s3DataKey = s3DataKey;
        this.s3MetadataKey = s3MetadataKey;
        this.bucketName = bucketName;
    }
}
