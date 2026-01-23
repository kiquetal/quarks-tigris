import { Component } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-mp3-upload',
  imports: [FormsModule],
  templateUrl: './mp3-upload.html',
  styleUrl: './mp3-upload.css',
})
export class Mp3Upload {
  selectedFile: File | null = null;
  email = '';

  constructor(private http: HttpClient) {}

  onFileSelected(event: any) {
    this.selectedFile = event.target.files[0];
  }

  onUpload() {
    if (this.selectedFile) {
      const formData = new FormData();
      formData.append('file', this.selectedFile, this.selectedFile.name);
      formData.append('email', this.email);
      this.http.post('/api/upload', formData).subscribe((res) => {
        console.log(res);
      });
    }
  }
}
