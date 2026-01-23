import { Component } from '@angular/core';
import { HttpClient, HttpClientModule } from '@angular/common/http';

@Component({
  selector: 'app-mp3-upload',
  imports: [HttpClientModule],
  templateUrl: './mp3-upload.html',
  styleUrl: './mp3-upload.css',
})
export class Mp3Upload {
  selectedFile: File | null = null;

  constructor(private http: HttpClient) {}

  onFileSelected(event: any) {
    this.selectedFile = event.target.files[0];
  }

  onUpload() {
    if (this.selectedFile) {
      const formData = new FormData();
      formData.append('file', this.selectedFile, this.selectedFile.name);
      this.http.post('/api/upload', formData).subscribe((res) => {
        console.log(res);
      });
    }
  }
}
