-- Allow up to 2 active attendance submissions per student, internship, and date.
ALTER TABLE attendance_records DROP CONSTRAINT ck_attendance_deletion_slot;
ALTER TABLE attendance_records ADD CONSTRAINT ck_attendance_deletion_slot
    CHECK ((deleted_at IS NULL AND active_slot IN (1, 2)) OR (deleted_at IS NOT NULL AND active_slot IS NULL));

-- Allow mentors to override automatic absence records to PRESENT and vice versa.
ALTER TABLE attendance_records DROP CONSTRAINT ck_attendance_automatic_absence;
ALTER TABLE attendance_records ADD CONSTRAINT ck_attendance_automatic_absence
    CHECK ((submission_type = 'AUTO_ABSENT' AND verification_basis = 'AUTOMATIC' AND attendance_result IN ('ABSENT', 'PRESENT'))
        OR (submission_type <> 'AUTO_ABSENT' AND verification_basis <> 'AUTOMATIC'));

