# Release Checklist

SemVer: MAJOR.MINOR.PATCH — pre-release: alpha → beta → rc → stable

Sync versions in:
1. build.gradle.kts → version = "x.x.x"
2. package.json → "version": "x.x.x"
3. git tag -a v0.1.0-rc.1 -m "chore(release): v0.1.0-rc.1"
4. git push origin v0.1.0-rc.1

Every RC must be tagged.
