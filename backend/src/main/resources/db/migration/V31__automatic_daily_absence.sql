-- Add a source for generated absences without modifying historical records or evidence.
ALTER TABLE attendance_records DROP CONSTRAINT ck_attendance_records_submission_type;
ALTER TABLE attendance_records ADD CONSTRAINT ck_attendance_records_submission_type
    CHECK (submission_type IN ('BASIC', 'PHYSICAL_CAPTURE', 'REMOTE_CAPTURE', 'AUTO_ABSENT'));
ALTER TABLE attendance_records DROP CONSTRAINT ck_attendance_verification_basis;
ALTER TABLE attendance_records ADD CONSTRAINT ck_attendance_verification_basis
    CHECK (verification_basis IN ('LEGACY', 'LIVENESS', 'AUTOMATIC'));
ALTER TABLE attendance_records ADD CONSTRAINT ck_attendance_automatic_absence
    CHECK ((submission_type = 'AUTO_ABSENT' AND attendance_result = 'ABSENT' AND verification_basis = 'AUTOMATIC')
        OR (submission_type <> 'AUTO_ABSENT' AND verification_basis <> 'AUTOMATIC'));
