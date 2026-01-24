import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
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
    this.apiService.validatePassphrase(this.passphrase).subscribe({
      next: (res) => {
        if (res.validated) {
          this.authService.login();
          this.router.navigate(['/upload']);
        } else {
          alert('Invalid passphrase');
        }
      },
      error: (err: HttpErrorResponse) => {
        console.error('Error validating passphrase:', err);

        if (err.status === 403) {
          // 403 Forbidden - invalid passphrase
          alert('Invalid passphrase');
        } else if (err.status >= 500) {
          // 5xx - Server error
          alert('Server error. Please try again later.');
        } else if (err.status === 0) {
          // Network error
          alert('Cannot connect to server. Please check your connection.');
        } else {
          // Other client errors (4xx)
          alert('Request failed. Please try again.');
        }
      }
    });
  }
}
