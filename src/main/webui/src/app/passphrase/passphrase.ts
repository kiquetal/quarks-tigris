import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../auth.service';
import { ApiService } from '../api.service';

@Component({
  selector: 'app-passphrase',
  imports: [FormsModule],
  templateUrl: './passphrase.html',
  styleUrl: './passphrase.css',
})
export class Passphrase {
  passphrase = '';

  constructor(
    private apiService: ApiService,
    private router: Router,
    private authService: AuthService
  ) {}

  validatePassphrase() {
    console.log('validatePassphrase called with:', this.passphrase);
    this.apiService.validatePassphrase(this.passphrase).subscribe({
      next: (res) => {
        console.log('Success callback received:', res);
        if (res.validated) {
          this.authService.login();
          this.router.navigate(['/upload']);
        } else {
          console.log('Passphrase invalid (success callback)');
          alert('Invalid passphrase');
        }
      },
      error: (err: HttpErrorResponse) => {
        console.log('Error callback received');
        console.error('Error details:', err);
        console.log('Status code:', err.status);
        console.log('Status text:', err.statusText);

        if (err.status === 403) {
          console.log('403 detected - showing alert');
          alert('Invalid passphrase (403)');
        } else if (err.status >= 500) {
          console.log('5xx detected - showing server error');
          alert('Server error. Please try again later.');
        } else if (err.status === 0) {
          console.log('Status 0 detected - network error');
          alert('Cannot connect to server. Please check your connection.');
        } else {
          console.log('Other 4xx detected:', err.status);
          alert('Request failed. Please try again.');
        }
      }
    });
  }
}
