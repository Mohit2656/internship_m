package edu.institution.ims.mentor;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AssignStudentsRequest(
        @NotEmpty(message = "Select at least one student")
        List<@NotNull(message = "Student ID is required") Long> studentUserIds
) {}
