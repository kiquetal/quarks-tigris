import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class EncryptionService {
  /**
   * Derives a cryptographic key from a passphrase using PBKDF2
   */
  private async deriveKey(passphrase: string, salt: Uint8Array): Promise<CryptoKey> {
    const encoder = new TextEncoder();
    const passphraseKey = await crypto.subtle.importKey(
      'raw',
      encoder.encode(passphrase),
      'PBKDF2',
      false,
      ['deriveKey']
    );

    return crypto.subtle.deriveKey(
      {
        name: 'PBKDF2',
        salt: salt,
        iterations: 100000,
        hash: 'SHA-256',
      },
      passphraseKey,
      { name: 'AES-GCM', length: 256 },
      false,
      ['encrypt', 'decrypt']
    );
  }

  /**
   * Encrypts a file using AES-GCM with the provided passphrase
   * @param file The file to encrypt
   * @param passphrase The passphrase to use for encryption
   * @returns A new File object containing the encrypted data with metadata
   */
  async encryptFile(file: File, passphrase: string): Promise<File> {
    // Generate random salt and IV
    const salt = crypto.getRandomValues(new Uint8Array(16));
    const iv = crypto.getRandomValues(new Uint8Array(12));

    // Derive encryption key from passphrase
    const key = await this.deriveKey(passphrase, salt);

    // Read file as ArrayBuffer
    const fileData = await file.arrayBuffer();

    // Encrypt the file data
    const encryptedData = await crypto.subtle.encrypt(
      {
        name: 'AES-GCM',
        iv: iv,
      },
      key,
      fileData
    );

    // Create a new blob with metadata (salt + iv + encrypted data)
    // This format: [16 bytes salt][12 bytes IV][encrypted data]
    const resultBuffer = new Uint8Array(salt.length + iv.length + encryptedData.byteLength);
    resultBuffer.set(salt, 0);
    resultBuffer.set(iv, salt.length);
    resultBuffer.set(new Uint8Array(encryptedData), salt.length + iv.length);

    // Create a new File with the encrypted data
    const encryptedFile = new File(
      [resultBuffer],
      file.name + '.encrypted',
      { type: 'application/octet-stream' }
    );

    return encryptedFile;
  }

  /**
   * Decrypts a file that was encrypted with encryptFile
   * @param encryptedFile The encrypted file
   * @param passphrase The passphrase used for encryption
   * @returns The decrypted file data as ArrayBuffer
   */
  async decryptFile(encryptedFile: File, passphrase: string): Promise<ArrayBuffer> {
    // Read the encrypted file
    const encryptedData = await encryptedFile.arrayBuffer();
    const encryptedArray = new Uint8Array(encryptedData);

    // Extract salt, IV, and encrypted data
    const salt = encryptedArray.slice(0, 16);
    const iv = encryptedArray.slice(16, 28);
    const data = encryptedArray.slice(28);

    // Derive decryption key
    const key = await this.deriveKey(passphrase, salt);

    // Decrypt the data
    const decryptedData = await crypto.subtle.decrypt(
      {
        name: 'AES-GCM',
        iv: iv,
      },
      key,
      data
    );

    return decryptedData;
  }
}
