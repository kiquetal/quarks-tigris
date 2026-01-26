package me.cresterida.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "File deletion response")
public class DeleteFileResponse {
    @Schema(description = "Status message")
    public String message;

    @Schema(description = "File ID that was deleted")
    public String fileId;

    @Schema(description = "File name that was deleted")
    public String fileName;

    @Schema(description = "Whether the file was successfully deleted")
    public boolean deleted;

    @Schema(description = "S3 key that was deleted")
    public String s3Key;

    public DeleteFileResponse(String message, String fileId, String fileName, boolean deleted, String s3Key) {
        this.message = message;
        this.fileId = fileId;
        this.fileName = fileName;
        this.deleted = deleted;
        this.s3Key = s3Key;
    }
}
