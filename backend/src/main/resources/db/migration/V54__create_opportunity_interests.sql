CREATE TABLE opportunity_interests (
    id BIGSERIAL PRIMARY KEY,
    opportunity_id BIGINT NOT NULL REFERENCES opportunities(id) ON DELETE CASCADE,
    student_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_opportunity_interest_student UNIQUE (opportunity_id, student_user_id)
);

CREATE INDEX idx_opportunity_interests_opportunity
    ON opportunity_interests (opportunity_id, created_at, id);

