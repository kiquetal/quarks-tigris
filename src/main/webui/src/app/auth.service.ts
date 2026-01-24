import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private isAuthenticated = false;
  private passphrase: string | null = null;

  login(passphrase: string) {
    this.isAuthenticated = true;
    this.passphrase = passphrase;
  }

  logout() {
    this.isAuthenticated = false;
    this.passphrase = null;
  }

  isLoggedIn() {
    return this.isAuthenticated;
  }

  getPassphrase(): string | null {
    return this.passphrase;
  }
}
