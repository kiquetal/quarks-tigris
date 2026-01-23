import { Component, signal } from '@angular/core';
import { Passphrase } from './passphrase/passphrase';
import { Mp3Upload } from './mp3-upload/mp3-upload';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-root',
  imports: [Passphrase, Mp3Upload, CommonModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('webui');
  isPassphraseValidated = false;

  onPassphraseValidated() {
    this.isPassphraseValidated = true;
  }
}
