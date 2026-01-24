package me.cresterida;

/**
 * Utility class to generate a master encryption key for envelope encryption.
 * Run this class to generate a new secure master key.
 */
public class MasterKeyGenerator {

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("Master Key Generator for Envelope Encryption");
        System.out.println("=".repeat(80));
        System.out.println();

        String masterKey = CryptoService.generateMasterKey();

        System.out.println("Generated Master Key (base64-encoded 256-bit AES key):");
        System.out.println(masterKey);
        System.out.println();
        System.out.println("Add this to your application.properties or environment variables:");
        System.out.println("encryption.master.key=" + masterKey);
        System.out.println();
        System.out.println("Or set as environment variable:");
        System.out.println("export ENCRYPTION_MASTER_KEY=\"" + masterKey + "\"");
        System.out.println();
        System.out.println("IMPORTANT: Keep this key secure and never commit it to version control!");
        System.out.println("=".repeat(80));
    }
}
