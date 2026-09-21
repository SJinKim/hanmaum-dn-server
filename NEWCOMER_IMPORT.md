# Newcomer cutover (HDN-184)

The importer accepts only a locally supplied CSV or XLSX export. It never calls
Google and the export must never be committed. Enable it for one controlled run:

```bash
./gradlew bootRun --args='--app.newcomer-import.enabled=true --app.newcomer-import.file=/secure/export.xlsx --app.newcomer-import.layout=SPREADSHEET_A --app.newcomer-import.dry-run=true'
```

Use `SPREADSHEET_A`, `SPREADSHEET_B`, or `GOOGLE_FORMS`. Header matching accepts
Korean and English aliases: 성/last name, 이름/first name, 이메일, 연락처, 생년월일,
성별, 세례, 신분, 출석현황, 담당자, 순/기수, and 첫방문일. Unknown columns are
ignored; a missing name produces a row-number-only error. Dates must be ISO
`YYYY-MM-DD` in CSV exports.

`gender` maps 남/남성/M and 여/여성/F. Baptism maps 입교, 미세례, 유아세례 and
일반세례/세례. Identity and attendance accept their enum names plus the documented
Korean aliases. A caregiver is resolved only through a local runtime property,
never a committed name-to-person fixture:

```yaml
app:
  newcomer-import:
    caregivers:
      "local-only-caregiver-label": "member-public-id"
    decisions:
      42: "member-public-id"
```

Rows with an existing email or same name are never merged automatically. The
dry-run report emits only a row number and a reason. Resolve each candidate in
`decisions`, rerun dry-run, then set `dry-run=false`. The importer records only
SHA-256 source/payload fingerprints and row numbers, making a retry of the same
export idempotent without storing export data.

## Cutover checklist

1. Freeze and export both spreadsheet views and Google Forms as local files.
2. Take a verified database backup and rehearse restoration in staging.
3. Run dry-run for each source; resolve every `MANUAL_RECONCILIATION_REQUIRED`,
   `UNRESOLVED_CAREGIVER`, and invalid-row report.
4. Compare report totals and an agreed, human-reviewed sample to each source.
5. Run with `dry-run=false`; rerun once and confirm every row is skipped.
6. Enable the new form, then set the old form to read-only or disable it.
