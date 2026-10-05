package edu.institution.ims.professionalprofile;

import jakarta.validation.constraints.*;

public record SkillNameRequest(@NotBlank(message = "Skill name is required")
        @Size(max = 100, message = "Skill name must be 100 characters or fewer") String name) {}
