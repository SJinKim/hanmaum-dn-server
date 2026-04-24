export default {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'type-enum': [2, 'always', [
      'feat', 'fix', 'docs', 'style', 'refactor',
      'perf', 'test', 'build', 'ci', 'chore', 'revert'
    ]],
    'scope-enum': [2, 'always', [
      'auth', 'member', 'api', 'db', 'config', 'ci', 'deps', 'release'
    ]],
    'scope-empty': [2, 'never'],
    'subject-case': [2, 'always', 'lower-case'],
    'subject-max-length': [2, 'always', 72],
    'body-max-line-length': [2, 'always', 100],
    'footer-max-line-length': [0],
  },
  parserPreset: {
    parserOpts: {
      referenceActions: ['refs', 'closes', 'fixes', 'resolves'],
      issuePrefixes: ['HDN-', '#']
    }
  }
};
