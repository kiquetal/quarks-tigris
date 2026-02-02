import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../environments/environment';

export interface PassphraseResponse {
  validated: boolean;
  sessionToken?: string;
  email?: string;
}

export interface UploadResponse {
  message: string;
  key: string;
  verified: boolean;
  originalSize: number;
}

export interface FileMetadata {
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

  uploadFile(file: File, email: string): Observable<UploadResponse> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    formData.append('email', email);
    return this.http.post<UploadResponse>(`${this.apiUrl}/upload`, formData);
  }

  getFiles(sessionToken: string): Observable<FileMetadata[]> {
    const headers = new HttpHeaders({
      'X-Session-Token': sessionToken
    });
    return this.http.get<FileMetadata[]>(`${this.apiUrl}/files`, { headers });
  }

  deleteFile(sessionToken: string, fileId: string, fileName: string): Observable<any> {
    const headers = new HttpHeaders({
      'X-Session-Token': sessionToken
    });
    const params = {
      fileId: fileId,
      fileName: fileName
    };
    return this.http.delete(`${this.apiUrl}/files`, { headers, params });
  }

  getMetadata(sessionToken: string, email: string, uuid: string): Observable<FileMetadata> {
    const headers = new HttpHeaders({
      'X-Session-Token': sessionToken
    });
    const params = {
      email: email,
      uuid: uuid
    };
    return this.http.get<FileMetadata>(`${this.apiUrl}/decrypt/metadata`, { headers, params });
  }
}
