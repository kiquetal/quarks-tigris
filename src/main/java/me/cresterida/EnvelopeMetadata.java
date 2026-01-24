package me.cresterida;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Metadata for envelope encryption.
 * Contains the KEK (Key Encryption Key) encrypted with master key.
 * The KEK was used to encrypt the file data, and is itself encrypted for secure storage.
 */
public class EnvelopeMetadata {

    @JsonProperty("version")
    public String version = "1.0";

    @JsonProperty("kek")
    public String kek;  // The KEK encrypted with master key (base64-encoded)

    @JsonProperty("algorithm")
    public String algorithm = "AES-GCM-256";

    @JsonProperty("original_filename")
    public String originalFilename;

    @JsonProperty("original_size")
    public long originalSize;

    @JsonProperty("encrypted_size")
    public long encryptedSize;

    @JsonProperty("verification_status")
    public String verificationStatus;

    @JsonProperty("timestamp")
    public long timestamp;

    public EnvelopeMetadata() {
        this.timestamp = System.currentTimeMillis();
    }

    public EnvelopeMetadata(String kek, String originalFilename, long originalSize,
                           long encryptedSize, boolean verified) {
        this();
        this.kek = kek;
        this.originalFilename = originalFilename;
        this.originalSize = originalSize;
        this.encryptedSize = encryptedSize;
        this.verificationStatus = verified ? "verified" : "not_verified";
    }
}
