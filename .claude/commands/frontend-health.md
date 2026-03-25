Your goal is to audit the Angular frontend for common issues.

Do the following:

1. Run `npm audit` in the frontend directory and fix with `npm audit fix`
2. Run `ng build --configuration production` and surface any errors or budget warnings
3. Run `ng lint` and report rule violations
4. Check for any `any` TypeScript types that should be properly typed
5. Look for Observables that are subscribed to manually but never unsubscribed (missing takeUntil or async pipe)
6. Verify all PrimeNG component imports are from the correct standalone or module path for PrimeNG v17+
7. Report all findings grouped by severity: Critical / Warning / Info
