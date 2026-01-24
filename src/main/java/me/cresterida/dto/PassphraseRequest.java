package me.cresterida.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Passphrase validation request")
public class PassphraseRequest {
    @Schema(description = "The passphrase to validate", required = true)
    public String passphrase;
}
