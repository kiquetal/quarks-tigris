import { Component, EventEmitter, Output } from '@angular/core';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-passphrase',
  imports: [HttpClientModule, FormsModule],
  templateUrl: './passphrase.html',
  styleUrl: './passphrase.css',
})
export class Passphrase {
  passphrase = '';
  @Output() passphraseValidated = new EventEmitter<void>();

  constructor(private http: HttpClient) {}

  validatePassphrase() {
    this.http
      .post('/api/validate-passphrase', { passphrase: this.passphrase })
      .subscribe((res: any) => {
        if (res.validated) {
          this.passphraseValidated.emit();
        } else {
          alert('Invalid passphrase');
        }
      });
  }
}
