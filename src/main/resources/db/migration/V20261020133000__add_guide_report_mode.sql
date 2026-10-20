-- HDN-331: imported/existing reports retain their summary-only provenance.
ALTER TABLE guide_worship_reports ADD COLUMN report_mode VARCHAR(16) NOT NULL DEFAULT 'SUMMARY_ONLY';
ALTER TABLE guide_worship_reports ADD CONSTRAINT guide_report_mode_valid CHECK (report_mode IN ('SUMMARY_ONLY', 'DETAILED'));
