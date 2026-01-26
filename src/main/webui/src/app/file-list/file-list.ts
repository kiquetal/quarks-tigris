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
  // Additional fields for display
  s3_data_key?: string;
  s3_metadata_key?: string;
  email?: string;
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
    // Get email from query params if available
    this.route.queryParams.subscribe(params => {
      if (params['email']) {
        this.email = params['email'];
        this.loadFiles();
      }
    });
  }

  loadFiles() {
    if (!this.email) {
      this.errorMessage = 'Please enter your email address';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';
    this.files = [];

    // TODO: Replace with actual API call when backend endpoint is ready
    // this.apiService.getFilesByEmail(this.email).subscribe({
    //   next: (files) => {
    //     this.files = files;
    //     this.isLoading = false;
    //   },
    //   error: (err) => {
    //     this.errorMessage = 'Failed to load files: ' + (err.error?.message || err.message);
    //     this.isLoading = false;
    //   }
    // });

    // Mock data for now
    setTimeout(() => {
      this.files = this.getMockFiles();
      this.isLoading = false;
    }, 1000);
  }

  getMockFiles(): FileMetadata[] {
    // Mock data - replace with actual API call
    return [
      {
        version: '1.0',
        kek: 'base64encodedkek1...',
        algorithm: 'AES-GCM-256',
        original_filename: 'my-audio-file.mp3',
        original_size: 5242880,
        encrypted_size: 5242908,
        verification_status: 'VERIFIED',
        timestamp: Date.now() - 3600000,
        s3_data_key: 'uploads/' + this.email + '/uuid1/my-audio-file.mp3.enc',
        s3_metadata_key: 'uploads/' + this.email + '/uuid1/metadata.json',
        email: this.email
      },
      {
        version: '1.0',
        kek: 'base64encodedkek2...',
        algorithm: 'AES-GCM-256',
        original_filename: 'another-song.mp3',
        original_size: 3145728,
        encrypted_size: 3145756,
        verification_status: 'VERIFIED',
        timestamp: Date.now() - 7200000,
        s3_data_key: 'uploads/' + this.email + '/uuid2/another-song.mp3.enc',
        s3_metadata_key: 'uploads/' + this.email + '/uuid2/metadata.json',
        email: this.email
      }
    ];
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
