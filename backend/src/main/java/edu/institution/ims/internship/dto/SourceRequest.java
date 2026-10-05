package edu.institution.ims.internship.dto;

import edu.institution.ims.internship.InternshipSource;
import jakarta.validation.constraints.NotNull;

public record SourceRequest(@NotNull(message = "Internship source is required") InternshipSource source) {}
