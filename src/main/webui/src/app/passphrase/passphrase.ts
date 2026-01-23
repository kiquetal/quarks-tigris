import { Component } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-passphrase',
  imports: [FormsModule],
  templateUrl: './passphrase.html',
  styleUrl: './passphrase.css',
})
export class Passphrase {
  passphrase = '';

  constructor(private http: HttpClient, private router: Router, private authService: AuthService) {}

  validatePassphrase() {
    this.http
      .post('/api/validate-passphrase', { passphrase: this.passphrase })
      .subscribe((res: any) => {
        if (res.validated) {
          this.authService.login();
          this.router.navigate(['/upload']);
        } else {
          alert('Invalid passphrase');
        }
      });
  }
}
