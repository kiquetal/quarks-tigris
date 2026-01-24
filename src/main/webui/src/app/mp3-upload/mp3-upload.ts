import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService } from '../api.service';
import { EncryptionService } from '../encryption.service';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-mp3-upload',
  imports: [FormsModule],
  templateUrl: './mp3-upload.html',
  styleUrl: './mp3-upload.css',
})
export class Mp3Upload {
  selectedFile: File | null = null;
  email = '';
  isEncrypting = false;

  constructor(
    private apiService: ApiService,
    private encryptionService: EncryptionService,
    private authService: AuthService
  ) {}

  onFileSelected(event: any) {
    this.selectedFile = event.target.files[0];
    if (this.selectedFile) {
      console.log('File selected:', {
        name: this.selectedFile.name,
        size: this.selectedFile.size,
        sizeInMB: (this.selectedFile.size / (1024 * 1024)).toFixed(2) + ' MB',
        type: this.selectedFile.type
      });
    }
  }

  async onUpload() {
    if (this.selectedFile) {
      // Get passphrase from auth service
      const passphrase = this.authService.getPassphrase();
      if (!passphrase) {
        alert('No passphrase found. Please login again.');
        return;
      }

      console.log('Starting upload...');
      console.log('File:', this.selectedFile.name, 'Size:', this.selectedFile.size, 'bytes');
      console.log('Email:', this.email);

      try {
        // Encrypt the file
        this.isEncrypting = true;
        console.log('Encrypting file with AES-GCM...');
        const encryptedFile = await this.encryptionService.encryptFile(this.selectedFile, passphrase);
        this.isEncrypting = false;
        console.log('Encryption complete. Encrypted size:', encryptedFile.size, 'bytes');

        // Upload the encrypted file
        this.apiService.uploadFile(encryptedFile, this.email).subscribe({
          next: (res) => {
            console.log('Upload successful:', res);
            alert('File uploaded successfully!');
          },
          error: (err: HttpErrorResponse) => {
            console.error('Upload failed:', err);
            console.log('Error status:', err.status);
            console.log('Error status text:', err.statusText);
            console.log('Error body:', err.error);

            if (err.status === 403) {
              // 403 Forbidden - unauthorized
              alert('You are not authorized to upload files.');
            } else if (err.status === 413) {
              // 413 Payload Too Large
              alert('File is too large. Please select a smaller file.');
            } else if (err.status >= 500) {
              // 5xx - Server error
              alert('Server error. Please try again later.');
            } else if (err.status === 0) {
              // Network error
              alert('Cannot connect to server. Please check your connection.');
            } else if (err.status >= 400 && err.status < 500) {
              // Other client errors (4xx)
              alert('Upload failed: ' + (err.error?.message || 'Invalid request'));
            } else {
              alert('Upload failed. Please try again.');
            }
          },
        });
      } catch (error) {
        this.isEncrypting = false;
        console.error('Encryption failed:', error);
        alert('Failed to encrypt file. Please try again.');
      }
    } else {
      console.warn('No file selected or email missing');
    }
  }
}
