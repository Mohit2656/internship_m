package edu.institution.ims.professionalprofile;

import java.time.Instant;

public record ProfessionalProfileResponse(Long id, String headline, String careerInterests,
        ResumeResponse resume, Instant createdAt, Instant updatedAt, Instant skillsUpdatedAt) {
    public static ProfessionalProfileResponse empty() {
        return new ProfessionalProfileResponse(null, null, null, null, null, null, null);
    }
}
