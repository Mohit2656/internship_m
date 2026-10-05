ALTER TABLE student_resumes DROP CONSTRAINT uk_student_resumes_profile;
ALTER TABLE student_resumes DROP CONSTRAINT chk_student_resumes_active;

ALTER TABLE student_resumes ADD COLUMN active_profile_id BIGINT;
UPDATE student_resumes SET active_profile_id = professional_profile_id WHERE is_active = TRUE;

ALTER TABLE student_resumes ADD CONSTRAINT uk_student_resumes_one_active UNIQUE (active_profile_id);
ALTER TABLE student_resumes ADD CONSTRAINT fk_student_resumes_active_profile
    FOREIGN KEY (active_profile_id) REFERENCES student_professional_profiles (id) ON DELETE CASCADE;
ALTER TABLE student_resumes ADD CONSTRAINT chk_student_resumes_active_reference CHECK (
    (is_active = TRUE AND active_profile_id = professional_profile_id)
    OR (is_active = FALSE AND active_profile_id IS NULL)
);

CREATE INDEX idx_student_resumes_profile ON student_resumes (professional_profile_id);
