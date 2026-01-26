import { Component, OnInit } from '@angular/core';
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
    private route: ActivatedRoute
  ) {}

  ngOnInit() {
    // Get email from query params or sessionStorage
    this.route.queryParams.subscribe(params => {
      if (params['email']) {
        this.email = params['email'];
      } else {
        // Try to get from session storage
        const storedEmail = sessionStorage.getItem('userEmail');
        if (storedEmail) {
          this.email = storedEmail;
        }
      }

      // Auto-load files if email is available
      if (this.email) {
        this.loadFiles();
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

    console.log('Loading files with session token:', sessionToken.substring(0, 8) + '...');

    // Call real API with session token
    this.apiService.getFiles(sessionToken).subscribe({
      next: (files) => {
        console.log('Files retrieved:', files);
        this.files = files;
        this.isLoading = false;
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
    if (confirm('Are you sure you want to delete ' + file.original_filename + '?')) {
      // TODO: Implement delete functionality
      console.log('Delete file:', file.original_filename);
      alert('Delete functionality coming soon!');
    }
  }

  goBack() {
    this.router.navigate(['/upload']);
  }
}
