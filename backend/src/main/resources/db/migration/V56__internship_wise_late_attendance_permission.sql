-- Add internship_id to late_attendance_permissions
ALTER TABLE late_attendance_permissions ADD COLUMN internship_id BIGINT REFERENCES internship_details (id);

-- Safely assign existing permissions to student's Internship 1 only (prevents accidental grant to Internship 2)
UPDATE late_attendance_permissions p
SET internship_id = (
    SELECT min(d.id)
    FROM internship_details d
    JOIN internship_onboarding o ON o.id = d.onboarding_id
    WHERE o.student_user_id = p.student_user_id AND o.internship_number = 1
)
WHERE p.internship_id IS NULL;

-- Remove any orphaned permissions that have no associated internship
DELETE FROM late_attendance_permission_audit
WHERE permission_id IN (SELECT id FROM late_attendance_permissions WHERE internship_id IS NULL);
DELETE FROM late_attendance_permissions WHERE internship_id IS NULL;

ALTER TABLE late_attendance_permissions ALTER COLUMN internship_id SET NOT NULL;

-- Replace old student-date constraint with student-internship-date constraint
ALTER TABLE late_attendance_permissions DROP CONSTRAINT IF EXISTS uk_late_attendance_permission_student_date;
ALTER TABLE late_attendance_permissions ADD CONSTRAINT uk_late_attendance_permission_student_internship_date
    UNIQUE (student_user_id, internship_id, attendance_date);

-- Add internship_id to late_attendance_permission_audit
ALTER TABLE late_attendance_permission_audit ADD COLUMN internship_id BIGINT REFERENCES internship_details (id);

UPDATE late_attendance_permission_audit a
SET internship_id = (
    SELECT p.internship_id
    FROM late_attendance_permissions p
    WHERE p.id = a.permission_id
)
WHERE a.internship_id IS NULL;

UPDATE late_attendance_permission_audit a
SET internship_id = (
    SELECT min(d.id)
    FROM internship_details d
    JOIN internship_onboarding o ON o.id = d.onboarding_id
    WHERE o.student_user_id = a.student_user_id AND o.internship_number = 1
)
WHERE a.internship_id IS NULL;
