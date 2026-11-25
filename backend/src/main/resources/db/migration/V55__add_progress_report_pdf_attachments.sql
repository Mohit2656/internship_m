ALTER TABLE weekly_progress_reports ADD COLUMN report_pdf_storage_key VARCHAR(80);
ALTER TABLE weekly_progress_reports ADD COLUMN report_pdf_original_name VARCHAR(255);
ALTER TABLE weekly_progress_reports ADD COLUMN report_pdf_size BIGINT;

ALTER TABLE monthly_progress_reports ADD COLUMN report_pdf_storage_key VARCHAR(80);
ALTER TABLE monthly_progress_reports ADD COLUMN report_pdf_original_name VARCHAR(255);
ALTER TABLE monthly_progress_reports ADD COLUMN report_pdf_size BIGINT;

