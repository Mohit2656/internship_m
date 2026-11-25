-- V59: Internship working schedule and daily attendance exceptions

ALTER TABLE internship_details
    ADD COLUMN IF NOT EXISTS working_days_per_week INTEGER DEFAULT 5,
    ADD COLUMN IF NOT EXISTS working_days VARCHAR(100) DEFAULT 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY';

CREATE TABLE IF NOT EXISTS attendance_exceptions (
    id BIGSERIAL PRIMARY KEY,
    internship_id BIGINT NOT NULL REFERENCES internship_details(id) ON DELETE CASCADE,
    student_user_id BIGINT NOT NULL REFERENCES users(id),
    exception_date DATE NOT NULL,
    exception_type VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by BIGINT REFERENCES users(id),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    review_remarks VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_attendance_exception_type CHECK (exception_type IN ('COMPANY_HOLIDAY', 'EXTRA_WORKDAY')),
    CONSTRAINT ck_attendance_exception_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT uk_attendance_exception_date UNIQUE (internship_id, exception_date, exception_type)
);

CREATE INDEX IF NOT EXISTS idx_attendance_exceptions_student_date 
    ON attendance_exceptions(student_user_id, exception_date);

CREATE INDEX IF NOT EXISTS idx_attendance_exceptions_internship 
    ON attendance_exceptions(internship_id, status);

CREATE TABLE IF NOT EXISTS attendance_exception_audit (
    id BIGSERIAL PRIMARY KEY,
    exception_id BIGINT NOT NULL REFERENCES attendance_exceptions(id) ON DELETE CASCADE,
    action VARCHAR(50) NOT NULL,
    actor_user_id BIGINT NOT NULL REFERENCES users(id),
    previous_status VARCHAR(20),
    new_status VARCHAR(20),
    remarks VARCHAR(500),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_attendance_exception_audit_exception 
    ON attendance_exception_audit(exception_id, occurred_at);

-- Backfill verified historical weekend attendance records as approved extra workdays
INSERT INTO attendance_exceptions (internship_id, student_user_id, exception_date, exception_type, reason, status, created_at, updated_at)
SELECT ar.internship_id, ar.student_user_id, ar.attendance_date, 'EXTRA_WORKDAY', 'Historical weekend attendance verified as present', 'APPROVED', ar.created_at, ar.updated_at
FROM attendance_records ar
WHERE ar.submission_type != 'AUTO_ABSENT'
  AND ar.attendance_result = 'PRESENT'
  AND EXTRACT(DOW FROM ar.attendance_date) IN (0, 6)
ON CONFLICT (internship_id, exception_date, exception_type) DO NOTHING;
