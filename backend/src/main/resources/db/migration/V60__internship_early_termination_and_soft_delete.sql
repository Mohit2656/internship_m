-- V60: Internship Early Termination and Soft Delete
-- Supports administrative early termination and soft deletion by III Cell Incharge

ALTER TABLE internship_details ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(30) DEFAULT 'ACTIVE' NOT NULL;
ALTER TABLE internship_details ADD COLUMN IF NOT EXISTS terminated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE internship_details ADD COLUMN IF NOT EXISTS terminated_by BIGINT REFERENCES users(id);
ALTER TABLE internship_details ADD COLUMN IF NOT EXISTS termination_reason VARCHAR(1000);
ALTER TABLE internship_details ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE internship_details ADD COLUMN IF NOT EXISTS deleted_by BIGINT REFERENCES users(id);
ALTER TABLE internship_details ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR(1000);

-- Update constraint to allow TERMINATED_EARLY
ALTER TABLE internship_details DROP CONSTRAINT IF EXISTS ck_internship_completion;
ALTER TABLE internship_details ADD CONSTRAINT ck_internship_completion CHECK (
    (completion_status = 'IN_PROGRESS' AND completed_at IS NULL AND completion_reviewer_id IS NULL AND completion_remarks IS NULL)
    OR (completion_status = 'COMPLETED' AND completed_at IS NOT NULL AND completion_reviewer_id IS NOT NULL)
    OR (completion_status = 'TERMINATED_EARLY' AND completed_at IS NOT NULL)
);

CREATE TABLE IF NOT EXISTS internship_lifecycle_audit (
    id BIGSERIAL PRIMARY KEY,
    internship_id BIGINT NOT NULL REFERENCES internship_details(id) ON DELETE CASCADE,
    student_user_id BIGINT NOT NULL REFERENCES users(id),
    action VARCHAR(50) NOT NULL,
    actor_user_id BIGINT NOT NULL REFERENCES users(id),
    effective_date DATE,
    reason VARCHAR(1000) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_internship_lifecycle_audit_internship 
    ON internship_lifecycle_audit(internship_id, occurred_at);
