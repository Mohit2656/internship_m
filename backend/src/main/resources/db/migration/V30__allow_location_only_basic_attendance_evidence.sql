-- Retain existing image validation; BASIC attendance can store GPS evidence without a photograph.
ALTER TABLE attendance_evidence ALTER COLUMN image_media_type DROP NOT NULL;
ALTER TABLE attendance_evidence DROP CONSTRAINT ck_attendance_evidence_image_size;
ALTER TABLE attendance_evidence ADD CONSTRAINT ck_attendance_evidence_image_size
    CHECK (image_size_bytes > 0 OR (image_size_bytes = 0 AND camera_capture_used = FALSE AND image_media_type IS NULL));
