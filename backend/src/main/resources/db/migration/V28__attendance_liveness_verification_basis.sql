-- Preserve every historical verification result and its evidence. The default
-- identifies older rows without inferring liveness from legacy GPS outcomes.
ALTER TABLE attendance_records ADD COLUMN verification_basis VARCHAR(20) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE attendance_records ADD CONSTRAINT ck_attendance_verification_basis
    CHECK (verification_basis IN ('LEGACY', 'LIVENESS'));
