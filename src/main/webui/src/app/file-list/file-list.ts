import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { ApiService } from '../api.service';
import { AuthService } from '../auth.service';

interface FileMetadata {
  version: string;
  kek: string;
  algorithm: string;
  original_filename: string;
  file_id: string;
  original_size: number;
  encrypted_size: number;
  verification_status: string;
  timestamp: number;
}

@Component({
  selector: 'app-file-list',
  imports: [CommonModule, FormsModule],
  templateUrl: './file-list.html',
  styleUrl: './file-list.css',
})
export class FileList implements OnInit {
  email = '';
  files: FileMetadata[] = [];
  isLoading = false;
  errorMessage = '';

  constructor(
    private apiService: ApiService,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit() {
    // Get email from sessionStorage first (set during login)
    const storedEmail = sessionStorage.getItem('userEmail');
    if (storedEmail) {
      this.email = storedEmail;
    }

    // Check query params (they override session storage)
    this.route.queryParams.subscribe(params => {
      if (params['email']) {
        this.email = params['email'];
      }

      // Always load files if we have an email (from either source)
      if (this.email) {
        console.log('Auto-loading files for email:', this.email);
        this.loadFiles();
      } else {
        console.log('No email available, user must enter it manually');
      }
    });
  }

  loadFiles() {
    if (!this.email) {
      this.errorMessage = 'Please enter your email address';
      return;
    }

    const sessionToken = sessionStorage.getItem('sessionToken');
    if (!sessionToken) {
      this.errorMessage = 'No active session. Please login again.';
      setTimeout(() => this.router.navigate(['/passphrase']), 2000);
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';
    this.files = [];
    this.cdr.detectChanges(); // Force UI update

    console.log('Loading files for email:', this.email);
    console.log('Using session token:', sessionToken.substring(0, 8) + '...');

    // Call real API with session token
    this.apiService.getFiles(sessionToken).subscribe({
      next: (files) => {
        console.log('Files retrieved successfully:', files.length, 'files');
        this.files = files;
        this.isLoading = false;
        this.cdr.detectChanges(); // Force UI update
      },
      error: (err) => {
        console.error('Error loading files:', err);

        if (err.status === 401) {
          this.errorMessage = 'Session expired. Please login again.';
          sessionStorage.clear();
          setTimeout(() => this.router.navigate(['/passphrase']), 2000);
        } else {
          this.errorMessage = 'Failed to load files: ' + (err.error?.error || err.message);
        }

        this.isLoading = false;
        this.cdr.detectChanges(); // Force UI update
      }
    });
  }

  formatFileSize(bytes: number): string {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
  }

  formatDate(timestamp: number): string {
    return new Date(timestamp).toLocaleString();
  }

  downloadFile(file: FileMetadata) {
    // TODO: Implement download functionality
    // This should decrypt the file using the passphrase and download it
    console.log('Download file:', file.original_filename);
    alert('Download functionality coming soon!\n\nFile: ' + file.original_filename);
  }

  deleteFile(file: FileMetadata) {
    if (!file.file_id) {
      alert('Error: File ID is missing. Cannot delete file.');
      console.error('File missing file_id:', file);
      return;
    }

    if (!file.original_filename) {
      alert('Error: File name is missing. Cannot delete file.');
      console.error('File missing original_filename:', file);
      return;
    }

    if (confirm('Are you sure you want to delete ' + file.original_filename + '?')) {
      const sessionToken = sessionStorage.getItem('sessionToken');
      if (!sessionToken) {
        this.errorMessage = 'No active session. Please login again.';
        setTimeout(() => this.router.navigate(['/passphrase']), 2000);
        return;
      }

      console.log('Deleting file:', file.original_filename);
      console.log('File ID:', file.file_id);
      console.log('File Name:', file.original_filename);

      // Call delete API
      this.apiService.deleteFile(sessionToken, file.file_id, file.original_filename).subscribe({
        next: (response) => {
          console.log('File deleted successfully:', response);
          alert('File "' + file.original_filename + '" deleted successfully!');

          // Remove file from local list
          this.files = this.files.filter(f => f.file_id !== file.file_id);
          this.cdr.detectChanges();
        },
        error: (err) => {
          console.error('Error deleting file:', err);
          let errorMsg = 'Failed to delete file';

          if (err.status === 401) {
            errorMsg = 'Session expired. Please login again.';
            sessionStorage.clear();
            setTimeout(() => this.router.navigate(['/passphrase']), 2000);
          } else if (err.error?.error) {
            errorMsg = err.error.error;
          } else if (err.message) {
            errorMsg += ': ' + err.message;
          }

          this.errorMessage = errorMsg;
          alert(errorMsg);
        }
      });
    }
  }

  goBack() {
    this.router.navigate(['/upload']);
  }
}
