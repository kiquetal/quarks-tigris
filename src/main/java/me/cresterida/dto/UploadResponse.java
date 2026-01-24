package me.cresterida.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "File upload response")
public class UploadResponse {
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
