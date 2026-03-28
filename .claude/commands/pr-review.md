Your goal is to run a full pre-PR quality check before pushing a branch.

1. Run `./gradlew test` on the backend — all tests must pass
2. Run `ng test --watch=false --browsers=ChromeHeadless` on the frontend — all tests must pass
3. Run `ng build --configuration production` — must succeed with no errors
4. Run `ng lint` — must pass with zero errors
5. Check for any TODO or FIXME comments introduced in this branch's changed files
6. Check no secrets, API keys, or localhost hardcoded URLs were added to tracked files
7. Verify new endpoints have appropriate `@PreAuthorize` or security config
8. Output a final PASS / FAIL summary with a list of any blocking issues
