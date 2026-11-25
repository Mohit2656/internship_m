-- Allow partial internship details saving by making non-essential internship columns nullable
ALTER TABLE internship_details ALTER COLUMN company_name DROP NOT NULL;
ALTER TABLE internship_details ALTER COLUMN company_type DROP NOT NULL;
ALTER TABLE internship_details ALTER COLUMN company_address DROP NOT NULL;
ALTER TABLE internship_details ALTER COLUMN company_phone DROP NOT NULL;
ALTER TABLE internship_details ALTER COLUMN industry_supervisor_name DROP NOT NULL;
ALTER TABLE internship_details ALTER COLUMN industry_supervisor_designation DROP NOT NULL;
ALTER TABLE internship_details ALTER COLUMN industry_supervisor_contact DROP NOT NULL;
ALTER TABLE internship_details ALTER COLUMN duration_months DROP NOT NULL;
ALTER TABLE internship_details ALTER COLUMN start_date DROP NOT NULL;
ALTER TABLE internship_details ALTER COLUMN end_date DROP NOT NULL;
ALTER TABLE internship_details ALTER COLUMN total_weeks DROP NOT NULL;
ALTER TABLE internship_details ALTER COLUMN internship_mode DROP NOT NULL;
ALTER TABLE internship_details ALTER COLUMN stipend_per_month DROP NOT NULL;
ALTER TABLE internship_details ALTER COLUMN full_time_employment_offered DROP NOT NULL;

-- Update constraints so they apply when values are provided
ALTER TABLE internship_details DROP CONSTRAINT chk_internship_details_dates;
ALTER TABLE internship_details ADD CONSTRAINT chk_internship_details_dates CHECK (
    start_date IS NULL OR end_date IS NULL OR end_date > start_date
);

ALTER TABLE internship_details DROP CONSTRAINT chk_internship_details_duration;
ALTER TABLE internship_details ADD CONSTRAINT chk_internship_details_duration CHECK (
    (duration_months IS NULL OR duration_months > 0) AND (total_weeks IS NULL OR total_weeks > 0)
);

ALTER TABLE internship_details DROP CONSTRAINT chk_internship_details_stipend;
ALTER TABLE internship_details ADD CONSTRAINT chk_internship_details_stipend CHECK (
    stipend_per_month IS NULL OR stipend_per_month >= 0
);
