import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../environments/environment';

export interface PassphraseResponse {
  validated: boolean;
}

export interface UploadResponse {
  message: string;
  key: string;
  verified: boolean;
  originalSize: number;
}

@Injectable({
  providedIn: 'root',
})
export class ApiService {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  validatePassphrase(passphrase: string): Observable<PassphraseResponse> {
    return this.http.post<PassphraseResponse>(`${this.apiUrl}/validate-passphrase`, {
      passphrase,
    });
  }

  uploadFile(file: File, email: string, passphrase: string): Observable<UploadResponse> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    formData.append('email', email);
    formData.append('passphrase', passphrase);
    return this.http.post<UploadResponse>(`${this.apiUrl}/upload`, formData);
  }
}
