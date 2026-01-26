package me.cresterida.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Passphrase validation response")
public class PassphraseResponse {
    @Schema(description = "Whether the passphrase is valid")
    public boolean validated;

    @Schema(description = "Session token (only present if validated=true)")
    public String sessionToken;

    @Schema(description = "User email (only present if validated=true)")
    public String email;

    public PassphraseResponse(boolean validated) {
        this.validated = validated;
    }
}
