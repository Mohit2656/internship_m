-- Existing EXIF coordinates, capture timestamp and file identity columns are reused.
ALTER TABLE monthly_progress_reports
    ADD COLUMN geo_verification_status VARCHAR(40);
ALTER TABLE monthly_progress_reports ADD COLUMN geo_verification_message VARCHAR(500);
ALTER TABLE monthly_progress_reports ADD COLUMN gps_altitude NUMERIC(18,6);
ALTER TABLE monthly_progress_reports ADD COLUMN gps_timestamp TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE monthly_progress_reports ADD COLUMN camera_make VARCHAR(255);
ALTER TABLE monthly_progress_reports ADD COLUMN camera_model VARCHAR(255);
ALTER TABLE monthly_progress_reports ADD COLUMN image_width INTEGER;
ALTER TABLE monthly_progress_reports ADD COLUMN image_height INTEGER;
ALTER TABLE monthly_progress_reports ADD COLUMN exif_present BOOLEAN;
ALTER TABLE monthly_progress_reports ADD COLUMN gps_present BOOLEAN;
ALTER TABLE monthly_progress_reports ADD COLUMN image_sha256 VARCHAR(64);
CREATE INDEX idx_monthly_geo_status ON monthly_progress_reports (geo_verification_status);
-- Legacy uploads remain unverified (NULL); their original bytes have not been re-extracted.
