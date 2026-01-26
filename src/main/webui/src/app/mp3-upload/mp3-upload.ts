import { Component, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { ApiService } from '../api.service';
import { EncryptionService } from '../encryption.service';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-mp3-upload',
  imports: [CommonModule, FormsModule],
  templateUrl: './mp3-upload.html',
  styleUrl: './mp3-upload.css',
})
export class Mp3Upload {
  selectedFile: File | null = null;
  email = '';
  isEncrypting = false;
  fileInput: HTMLInputElement | null = null;

  constructor(
    private apiService: ApiService,
    private encryptionService: EncryptionService,
    private authService: AuthService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}

  onFileSelected(event: any) {
    this.fileInput = event.target;
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

  resetForm() {
    this.selectedFile = null;
    this.email = '';
    this.isEncrypting = false;
    if (this.fileInput) {
      this.fileInput.value = '';
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
        this.cdr.detectChanges(); // Force UI update

        const encryptedFile = await this.encryptionService.encryptFile(this.selectedFile, passphrase);
        console.log('Encryption complete. Encrypted size:', encryptedFile.size, 'bytes');

        // Upload the encrypted file
        this.apiService.uploadFile(encryptedFile, this.email)
          .pipe(
            finalize(() => {
              // This always runs, whether success or error
              this.isEncrypting = false;
              this.cdr.detectChanges(); // Force UI update
              console.log('Upload finished, isEncrypting set to false');
            })
          )
          .subscribe({
          next: (res) => {
            console.log('Upload successful:', res);
            alert('File uploaded successfully!');

            // Clear the form completely
            this.resetForm();

            // Redirect to index
            this.router.navigate(['/']);
          },
          error: (err: HttpErrorResponse) => {
            console.error('Upload failed:', err);
            console.log('Error status:', err.status);
            console.log('Error status text:', err.statusText);
            console.log('Error body:', err.error);

            // Determine error message
            let errorMessage = 'Upload failed. Please try again.';

            if (err.status === 403) {
              errorMessage = 'You are not authorized to upload files.';
            } else if (err.status === 413) {
              errorMessage = 'File is too large. Please select a smaller file.';
            } else if (err.status >= 500) {
              errorMessage = 'Server error. Please try again later.';
            } else if (err.status === 0) {
              errorMessage = 'Cannot connect to server. Please check your connection.';
            } else if (err.status >= 400 && err.status < 500) {
              errorMessage = 'Upload failed: ' + (err.error?.message || 'Invalid request');
            }

            alert(errorMessage);

            // Reset file input to allow re-selection
            if (this.fileInput) {
              this.fileInput.value = '';
            }
          },
        });
      } catch (error) {
        this.isEncrypting = false;
        this.cdr.detectChanges(); // Force UI update
        console.error('Encryption failed:', error);
        alert('Failed to encrypt file. Please try again.');

        // Reset file input to allow re-selection
        if (this.fileInput) {
          this.fileInput.value = '';
        }
      }
    } else {
      console.warn('No file selected or email missing');
    }
  }

  viewFiles() {
    // TODO: Navigate to files list page or show modal with uploaded files
    // For now, just show an alert - you can implement a proper file list component later
    alert('View Files feature - Coming soon!\n\nThis will show your uploaded files from S3 using the metadata.');
    console.log('View Files clicked - implement file listing here');

    // Example navigation when you create a file list component:
    // this.router.navigate(['/files'], { queryParams: { email: this.email } });
  }
}
