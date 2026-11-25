ALTER TABLE attendance_records ADD COLUMN mentor_seen_by BIGINT REFERENCES users(id);
ALTER TABLE attendance_records ADD COLUMN mentor_seen_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE attendance_records ADD CONSTRAINT ck_attendance_mentor_seen
    CHECK ((mentor_seen_by IS NULL AND mentor_seen_at IS NULL)
        OR (mentor_seen_by IS NOT NULL AND mentor_seen_at IS NOT NULL));
