CREATE INDEX idx_users_role_active ON users (role, active);
CREATE INDEX idx_internship_details_stipend ON internship_details (stipend_per_month);
CREATE INDEX idx_internship_details_full_time ON internship_details (full_time_employment_offered);
