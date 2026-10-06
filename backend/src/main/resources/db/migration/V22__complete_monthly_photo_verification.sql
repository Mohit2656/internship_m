-- V20 and V21 have already been deployed; preserve their schema history unchanged.
-- Reuse V21 photo columns and add only the missing capture-time status.
ALTER TABLE monthly_progress_reports ALTER COLUMN exif_latitude TYPE NUMERIC(12,9);
ALTER TABLE monthly_progress_reports ALTER COLUMN exif_longitude TYPE NUMERIC(12,9);
ALTER TABLE monthly_progress_reports ADD COLUMN capture_time_status VARCHAR(32);
-- Existing capture-time statuses remain unknown until the original bytes are assessed.
