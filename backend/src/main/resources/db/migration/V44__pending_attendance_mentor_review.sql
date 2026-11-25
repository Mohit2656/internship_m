ALTER TABLE attendance_records DROP CONSTRAINT ck_attendance_records_result;
ALTER TABLE attendance_records ALTER COLUMN attendance_result TYPE VARCHAR(30);
ALTER TABLE attendance_records ADD CONSTRAINT ck_attendance_records_result
    CHECK (attendance_result IN ('PRESENT', 'PENDING_MENTOR_REVIEW', 'ABSENT'));
