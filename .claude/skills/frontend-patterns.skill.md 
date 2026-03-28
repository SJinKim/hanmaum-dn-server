# Frontend Patterns

- Auth: angular-oauth2-oidc PKCE, realm=hanmaum, client=hanmaum-dashboard
- API base: http://localhost:8080/api/v1/
- Routing: lazy-loaded modules, authGuard on all private routes
- Style: Prettier, printWidth:100, singleQuote:true

## Rules
- No `any` — use unknown + narrow
- async pipe over manual subscriptions
- Manual subs: takeUntilDestroyed()
- No business logic in components → delegate to services
- API types → TypeScript interface in models/
- Env config → environment.ts / environment.prod.ts only

## Dumb Frontend Rule
- No business logic in components or services — backend decides everything
- No hardcoded labels, statuses, or config values in TypeScript/HTML
- Enums and status lists are fetched from API, not duplicated in frontend
- Components only: receive data → render → emit events
