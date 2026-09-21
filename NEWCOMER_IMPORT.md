# Newcomer cutover (HDN-184)

The importer accepts only a locally supplied CSV or XLSX export. It never calls
Google and the export must never be committed. Enable it for one controlled run:

```bash
./gradlew bootRun --args='--app.newcomer-import.enabled=true --app.newcomer-import.file=/secure/export.xlsx --app.newcomer-import.layout=SPREADSHEET_A --app.newcomer-import.dry-run=true'
```

Use `SPREADSHEET_A`, `SPREADSHEET_B`, or `GOOGLE_FORMS`. Every source header
must be mapped below; an unmapped header aborts the run before any row is
processed, so the cutover cannot silently drop a field.

| Target | Spreadsheet A | Spreadsheet B | Google Forms |
| --- | --- | --- | --- |
| `members.lastName` / `firstName` | 성, 이름; last name, first name | same | same |
| `members.email` / `phoneNumber` | 이메일/email, 연락처/전화번호/phone | same | same |
| `members.birthDate` / `gender` / `baptism` | 생년월일, 성별, 세례 | same | same |
| `members.registrationDate` | 등록일/registration date | same | Timestamp/제출시간 |
| `newcomer_profiles.intakeRound` | 순/기수 | same | same, if present |
| `newcomer_profiles.caregiver` | 담당자/caregiver | same | same, if present |
| `newcomer_profiles.identityStatus` / `postAssignmentAttendance` | 신분, 출석현황 | same | same, if present |
| `newcomer_profiles.firstVisitDate` | 첫방문일/first visit date | first visit/첫방문일 | first visit date/첫방문일 |

Dates accept ISO `YYYY-MM-DD`; Google Forms timestamps additionally accept its
common US and Korean CSV representations. `Timestamp` is the form-submission
date, not an inferred first-visit date.

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

Rows with an existing email, name, or normalized phone number are never merged
automatically; a matching birth date is included as corroborating match evidence.
The dry-run report emits only a row number, a reason, candidate public IDs, and
non-sensitive match reasons. Resolve each candidate in `decisions`, rerun dry-run,
then set `dry-run=false`. The importer records only SHA-256 source/payload
fingerprints and row numbers, making a retry of the same export idempotent without
storing export data.

## Cutover checklist

1. Freeze and export both spreadsheet views and Google Forms as local files.
2. Take a verified database backup and rehearse restoration in staging.
3. Run dry-run for each source; resolve every `MANUAL_RECONCILIATION_REQUIRED`,
   `UNRESOLVED_CAREGIVER`, and invalid-row report.
4. Compare report totals and an agreed, human-reviewed sample to each source.
5. Run with `dry-run=false`; rerun once and confirm every row is skipped.
6. Enable the new form, then set the old form to read-only or disable it.

## Backup and rollback evidence

Before the production run, record the backup location, checksum, restore time,
and the source row counts in the cutover ticket. Restore that backup into staging
and run the same dry-run there before production. If the post-import totals or
sample differ from the frozen source, restore the verified backup and keep the
old form read-only until the discrepancy is resolved. The automated retry test
proves that rerunning an unchanged export creates no additional records; the
staging restore rehearsal remains a required operational sign-off.
