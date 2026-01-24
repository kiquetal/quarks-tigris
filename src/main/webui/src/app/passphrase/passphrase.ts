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
    this.apiService.validatePassphrase(this.passphrase).subscribe((res) => {
      if (res.validated) {
        this.authService.login();
        this.router.navigate(['/upload']);
      } else {
        alert('Invalid passphrase');
      }
    });
  }
}
