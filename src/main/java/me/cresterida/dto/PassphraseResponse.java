package me.cresterida.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Passphrase validation response")
public class PassphraseResponse {
    @Schema(description = "Whether the passphrase is valid")
    public boolean validated;

    public PassphraseResponse(boolean validated) {
        this.validated = validated;
    }
}
