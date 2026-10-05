CREATE TABLE attendance_verification_sessions (
    id VARCHAR(36) NOT NULL,
    student_user_id BIGINT NOT NULL,
    internship_id BIGINT NOT NULL,
    attendance_date DATE NOT NULL,
    challenge_type VARCHAR(30) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_attendance_verification_sessions PRIMARY KEY (id),
    CONSTRAINT fk_attendance_verification_sessions_student FOREIGN KEY (student_user_id) REFERENCES users (id),
    CONSTRAINT fk_attendance_verification_sessions_internship FOREIGN KEY (internship_id) REFERENCES internship_details (id),
    CONSTRAINT ck_attendance_verification_sessions_challenge CHECK (
        challenge_type IN ('BLINK', 'TURN_HEAD_LEFT', 'TURN_HEAD_RIGHT')
    ),
    CONSTRAINT ck_attendance_verification_sessions_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_attendance_verification_sessions_consumed CHECK (
        consumed_at IS NULL OR consumed_at >= created_at
    )
);

CREATE INDEX idx_attendance_verification_sessions_student_date
    ON attendance_verification_sessions (student_user_id, attendance_date);
CREATE INDEX idx_attendance_verification_sessions_expiry
    ON attendance_verification_sessions (expires_at);

ALTER TABLE attendance_evidence ADD COLUMN ai_verification_session_id VARCHAR(36);
ALTER TABLE attendance_evidence ADD COLUMN ai_challenge_type VARCHAR(30);
ALTER TABLE attendance_evidence ADD COLUMN ai_face_detected BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE attendance_evidence ADD COLUMN ai_single_face BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE attendance_evidence ADD COLUMN ai_quality_passed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE attendance_evidence ADD COLUMN ai_liveness_passed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE attendance_evidence ADD COLUMN ai_model_version VARCHAR(120);
ALTER TABLE attendance_evidence ADD COLUMN ai_processed_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE attendance_evidence ADD COLUMN ai_failure_reason VARCHAR(80);
ALTER TABLE attendance_evidence ADD CONSTRAINT fk_attendance_evidence_ai_session
    FOREIGN KEY (ai_verification_session_id) REFERENCES attendance_verification_sessions (id);
ALTER TABLE attendance_evidence ADD CONSTRAINT uk_attendance_evidence_ai_session
    UNIQUE (ai_verification_session_id);
ALTER TABLE attendance_evidence ADD CONSTRAINT ck_attendance_evidence_ai_challenge CHECK (
    ai_challenge_type IS NULL OR ai_challenge_type IN ('BLINK', 'TURN_HEAD_LEFT', 'TURN_HEAD_RIGHT')
);

