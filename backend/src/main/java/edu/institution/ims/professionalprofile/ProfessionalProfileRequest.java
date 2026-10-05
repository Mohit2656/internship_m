package edu.institution.ims.professionalprofile;

import jakarta.validation.constraints.Size;

public record ProfessionalProfileRequest(
        @Size(max = 160, message = "Headline must be 160 characters or fewer") String headline,
        @Size(max = 1000, message = "Career interests must be 1000 characters or fewer") String careerInterests) {}
