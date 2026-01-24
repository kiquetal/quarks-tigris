package me.cresterida.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Error response")
public class ErrorResponse {
    @Schema(description = "Error message")
    public String message;

    public ErrorResponse(String message) {
        this.message = message;
    }
}
