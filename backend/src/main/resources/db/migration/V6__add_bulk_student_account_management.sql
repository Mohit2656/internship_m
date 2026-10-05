ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE student_accounts ADD COLUMN student_name VARCHAR(150);
ALTER TABLE student_accounts ADD COLUMN uid VARCHAR(50);
ALTER TABLE student_accounts ADD COLUMN semester INTEGER;

ALTER TABLE student_accounts ADD CONSTRAINT uk_student_accounts_uid UNIQUE (uid);
ALTER TABLE student_accounts ADD CONSTRAINT chk_student_accounts_semester CHECK (semester IS NULL OR semester BETWEEN 1 AND 8);
CREATE INDEX idx_users_must_change_password ON users (must_change_password);
