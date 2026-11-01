ALTER TABLE internship_locations DROP CONSTRAINT ck_workplace_dual_approval;
ALTER TABLE internship_locations DROP CONSTRAINT ck_workplace_status;
ALTER TABLE internship_locations ALTER COLUMN status TYPE VARCHAR(30);

ALTER TABLE internship_locations ADD CONSTRAINT ck_workplace_status
    CHECK(status IN ('PENDING','PENDING_MENTOR_APPROVAL','CONFIRMED','APPROVED','REJECTED'));
ALTER TABLE internship_locations ADD CONSTRAINT ck_workplace_mentor_approval
    CHECK(status<>'APPROVED' OR (
        mentor_decision='APPROVE'
        AND mentor_approved_by IS NOT NULL
        AND mentor_reviewed_at IS NOT NULL
    ));

-- Preserve all historical reviewer fields and audit rows. Only recompute the effective
-- workflow status under the mentor-only policy.
UPDATE internship_locations
SET status = CASE
    WHEN mentor_decision='APPROVE' AND mentor_approved_by IS NOT NULL AND mentor_reviewed_at IS NOT NULL THEN 'APPROVED'
    WHEN mentor_decision='REJECT' THEN 'REJECTED'
    ELSE 'PENDING_MENTOR_APPROVAL'
END;
