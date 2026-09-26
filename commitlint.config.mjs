export default {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'type-enum': [2, 'always', [
      'feat', 'fix', 'docs', 'style', 'refactor',
      'perf', 'test', 'build', 'ci', 'chore', 'revert'
    ]],
    'scope-enum': [2, 'always', [
      'auth', 'member', 'api', 'db', 'config', 'ci', 'deps', 'release', 'cleanup'
    ]],
    'scope-empty': [2, 'never'],
    'subject-case': [2, 'always', 'lower-case'],
    'subject-max-length': [2, 'always', 72],
    'body-max-line-length': [2, 'always', 100],
    'footer-max-line-length': [0],
    'no-ai-attribution': [2, 'always'],
  },
  plugins: [{
    rules: {
      // No AI identity in authorship or trailers — a co-author line naming
      // Claude/Anthropic or a "Generated with" footer fails the commit.
      'no-ai-attribution': ({ raw }) => [
        !/co-authored-by:[^\n]*(claude|anthropic)|generated with \[?claude/i.test(raw),
        'AI attribution is not allowed (Co-Authored-By: Claude, "Generated with Claude Code")',
      ],
    },
  }],
  parserPreset: {
    parserOpts: {
      referenceActions: ['refs', 'closes', 'fixes', 'resolves'],
      issuePrefixes: ['HDN-', '#']
    }
  }
};
