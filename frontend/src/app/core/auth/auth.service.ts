import {inject, Injectable, signal} from '@angular/core';
import {OAuthService} from 'angular-oauth2-oidc';
import {authConfig} from './auth.config';

@Injectable({providedIn: 'root'})
export class AuthService {
  private oauthService = inject(OAuthService);

  // Zoneless State
  private _profile = signal<any>(null);
  profile = this._profile.asReadonly();

  // Init (App Start aufgerufen)
  async init(): Promise<void> {
    this.oauthService.configure(authConfig);
    this.oauthService.setupAutomaticSilentRefresh();
    await this.oauthService.loadDiscoveryDocumentAndTryLogin();

    if (this.oauthService.hasValidAccessToken()) {
      this.loadProfile();
    }
  }

  login() {
    this.oauthService.initCodeFlow();
  }

  logout() {
    this.oauthService.logOut();
    this._profile.set(null);
  }

  // Hilfsmethode für ein Guard
  isLoggedIn(): boolean {
    return this.oauthService.hasValidAccessToken();
  }

  getAccessToken(): string {
    return this.oauthService.getAccessToken();
  }

  private loadProfile(): void {
    const claims = this.oauthService.getIdentityClaims();
    if(claims) {
      this._profile.set(claims);
    }
  }
}
