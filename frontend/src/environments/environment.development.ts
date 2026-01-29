export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api/v1/', // Spring-API Basis
  keycloak: {
    url: 'http://localhost:8091', // KC-Basis
    realm: 'hanmaum',
    clientId: 'hanmaum-dashboard',
    checkLoginIframe: false
  }
}
