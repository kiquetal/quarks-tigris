import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService } from '../api.service';

@Component({
  selector: 'app-mp3-upload',
  imports: [FormsModule],
  templateUrl: './mp3-upload.html',
  styleUrl: './mp3-upload.css',
})
export class Mp3Upload {
  selectedFile: File | null = null;
  email = '';

  constructor(private apiService: ApiService) {}

  onFileSelected(event: any) {
    this.selectedFile = event.target.files[0];
  }

  onUpload() {
    if (this.selectedFile) {
      this.apiService.uploadFile(this.selectedFile, this.email).subscribe({
        next: (res) => {
          console.log('Upload successful:', res);
          alert(`File uploaded successfully! URL: ${res.fileUrl}`);
        },
        error: (err: HttpErrorResponse) => {
          console.error('Upload failed:', err);

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
    }
  }
}
