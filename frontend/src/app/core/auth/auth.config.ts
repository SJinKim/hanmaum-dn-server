import { AuthConfig } from 'angular-oauth2-oidc';
import { environment } from '../../../environments/environment.development';

export const authConfig: AuthConfig = {
  // 1. Wer stellt die Tokens aus? (Dein Keycloak Container)
  // Wichtig: Im Browser ist es localhost:8091 (externer Port), nicht der interne Docker-Port!
  issuer: environment.keycloak.url + '/realms/' + environment.keycloak.realm,

  // 2. Wer will sich einloggen? (Muss exakt mit Keycloak übereinstimmen)
  clientId: environment.keycloak.clientId,

  // 3. Wohin zurück nach dem Login?
  redirectUri: window.location.origin + '/index.html',

  // 4. Was wollen wir dürfen?
  scope: 'openid profile email offline_access',

  // 5. Sicherheitseinstellungen (PKCE Flow ist heute Standard)
  responseType: 'code',
  showDebugInformation: !environment.production, // Nur für Dev, später false setzen
  requireHttps: false, // Da wir auf localhost sind
  useSilentRefresh: true,
};
