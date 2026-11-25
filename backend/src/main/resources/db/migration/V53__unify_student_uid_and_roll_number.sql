-- The institution uses UID and roll number as one student-owned identifier.
-- UID is already globally unique, so it is the safe canonical value for legacy rows.
UPDATE student_profiles
SET roll_number = uid
WHERE roll_number IS DISTINCT FROM uid;

-- Remove stale imported values that now belong to another student's canonical profile.
UPDATE student_accounts sa
SET uid = NULL
WHERE EXISTS (
    SELECT 1
    FROM student_profiles sp
    WHERE sp.uid = sa.uid
      AND sp.user_id <> sa.user_id
);

UPDATE student_accounts sa
SET uid = sp.uid
FROM student_profiles sp
WHERE sp.user_id = sa.user_id
  AND sa.uid IS DISTINCT FROM sp.uid;
