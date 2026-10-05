package edu.institution.ims.mentor;

import jakarta.validation.constraints.*;
import java.util.Locale;

public record MentorAccountRequest(
        @NotBlank(message = "Name is required") @Size(max = 150) String name,
        @NotBlank(message = "Employee ID is required") @Size(max = 50) String employeeId,
        @NotBlank(message = "Institute email is required") @Email(message = "Institute email format is invalid") @Size(max = 254) String instituteEmail,
        @NotBlank(message = "Department is required") @Size(max = 150) String department,
        @NotBlank(message = "Designation is required") @Size(max = 150) String designation,
        @NotBlank(message = "Phone is required") @Pattern(regexp = "^\\+?[0-9][0-9 -]{7,18}[0-9]$", message = "Phone format is invalid") String phone
) {
    public MentorAccountRequest {
        name = clean(name); employeeId = clean(employeeId); instituteEmail = clean(instituteEmail);
        department = clean(department); designation = clean(designation); phone = clean(phone);
        if (employeeId != null) employeeId = employeeId.toUpperCase(Locale.ROOT);
        if (instituteEmail != null) instituteEmail = instituteEmail.toLowerCase(Locale.ROOT);
    }
    private static String clean(String value) { return value == null ? null : value.trim(); }
}
