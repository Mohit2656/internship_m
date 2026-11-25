ALTER TABLE student_profiles ADD COLUMN current_cgpa NUMERIC(4, 2);
ALTER TABLE student_profiles ADD CONSTRAINT ck_student_profiles_current_cgpa
    CHECK (current_cgpa IS NULL OR (current_cgpa >= 0.00 AND current_cgpa <= 10.00));
