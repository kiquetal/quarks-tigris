import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
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
        error: (err) => {
          console.error('Upload failed:', err);
          alert('Upload failed. Please try again.');
        },
      });
    }
  }
}
