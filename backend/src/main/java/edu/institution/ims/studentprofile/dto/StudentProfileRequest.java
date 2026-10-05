package edu.institution.ims.studentprofile.dto;

import edu.institution.ims.studentprofile.Branch;
import edu.institution.ims.studentprofile.validation.AcademicYear;
import jakarta.validation.constraints.*;

public record StudentProfileRequest(
        @NotBlank(message = "Student name is required") @Size(max = 150) String studentName,
        Branch branch,
        @NotBlank(message = "UID is required") @Size(max = 50) String uid,
        @NotBlank(message = "Roll number is required") @Size(max = 50) String rollNumber,
        @NotBlank(message = "Permanent address is required") @Size(max = 1000) String permanentAddress,
        @Size(max = 1000) String currentAddress,
        @NotBlank(message = "Personal email is required") @Email(message = "Personal email format is invalid") @Size(max = 254) String personalEmail,
        @NotBlank(message = "Institute email is required") @Email(message = "Institute email format is invalid") @Size(max = 254) String instituteEmail,
        @NotBlank(message = "Phone number is required") @Pattern(regexp = "^\\+?[0-9][0-9 -]{7,18}[0-9]$", message = "Phone number format is invalid") String phoneNumber,
        @NotBlank(message = "Parent or guardian name is required") @Size(max = 150) String guardianName,
        @NotBlank(message = "Parent or guardian contact is required") @Pattern(regexp = "^\\+?[0-9][0-9 -]{7,18}[0-9]$", message = "Guardian contact format is invalid") String guardianContact,
        @NotBlank(message = "Emergency contact is required") @Pattern(regexp = "^\\+?[0-9][0-9 -]{7,18}[0-9]$", message = "Emergency contact format is invalid") String emergencyContact,
        @NotBlank(message = "Blood group is required") @Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "Blood group is invalid") String bloodGroup,
        @Pattern(regexp = "^$|^(?:[0-9][ -]?){11}[0-9]$", message = "Aadhaar number must contain 12 digits") String aadhaarNumber,
        @NotBlank(message = "Faculty mentor name is required") @Size(max = 150) String facultyMentorName,
        @NotBlank(message = "Faculty mentor contact is required") @Pattern(regexp = "^\\+?[0-9][0-9 -]{7,18}[0-9]$", message = "Faculty mentor contact format is invalid") String facultyMentorContact,
        @NotBlank(message = "Internship coordinator name is required") @Size(max = 150) String internshipCoordinatorName,
        @NotBlank(message = "Internship coordinator contact is required") @Pattern(regexp = "^\\+?[0-9][0-9 -]{7,18}[0-9]$", message = "Internship coordinator contact format is invalid") String internshipCoordinatorContact,
        @NotBlank(message = "CDC coordinator name is required") @Size(max = 150) String cdcCoordinatorName,
        @NotBlank(message = "CDC coordinator contact is required") @Pattern(regexp = "^\\+?[0-9][0-9 -]{7,18}[0-9]$", message = "CDC coordinator contact format is invalid") String cdcCoordinatorContact,
        @NotBlank(message = "Academic year is required") @AcademicYear String academicYear,
        @NotNull(message = "Semester is required") @Min(value = 1, message = "Semester must be between 1 and 8") @Max(value = 8, message = "Semester must be between 1 and 8") Integer semester
) {}
