package edu.institution.ims.studentprofile.dto;

import edu.institution.ims.studentprofile.Branch;
import edu.institution.ims.studentprofile.validation.AcademicYear;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record StudentProfileRequest(
        @Size(max = 150) String studentName,
        Branch branch,
        @Size(max = 50) String uid,
        @Size(max = 50) String rollNumber,
        @Size(max = 1000) String permanentAddress,
        @Size(max = 1000) String currentAddress,
        @Email(message = "Personal email format is invalid") @Size(max = 254) String personalEmail,
        @Email(message = "Institute email format is invalid") @Size(max = 254) String instituteEmail,
        @Pattern(regexp = "^$|^\\+?[0-9][0-9 -]{7,18}[0-9]$", message = "Phone number format is invalid") String phoneNumber,
        @Size(max = 150) String guardianName,
        @Pattern(regexp = "^$|^\\+?[0-9][0-9 -]{7,18}[0-9]$", message = "Guardian contact format is invalid") String guardianContact,
        @Pattern(regexp = "^$|^\\+?[0-9][0-9 -]{7,18}[0-9]$", message = "Emergency contact format is invalid") String emergencyContact,
        @Pattern(regexp = "^$|^(A|B|AB|O)[+-]$", message = "Blood group is invalid") String bloodGroup,
        @Pattern(regexp = "^$|^(?:[0-9][ -]?){11}[0-9]$", message = "Aadhaar number must contain 12 digits") String aadhaarNumber,
        @Size(max = 150) String facultyMentorName,
        @Pattern(regexp = "^$|^\\+?[0-9][0-9 -]{7,18}[0-9]$", message = "Faculty mentor contact format is invalid") String facultyMentorContact,
        @Size(max = 150) String internshipCoordinatorName,
        @Pattern(regexp = "^$|^\\+?[0-9][0-9 -]{7,18}[0-9]$", message = "Internship coordinator contact format is invalid") String internshipCoordinatorContact,
        @Size(max = 150) String cdcCoordinatorName,
        @Pattern(regexp = "^$|^\\+?[0-9][0-9 -]{7,18}[0-9]$", message = "CDC coordinator contact format is invalid") String cdcCoordinatorContact,
        @AcademicYear String academicYear,
        @Min(value = 1, message = "Semester must be between 1 and 8") @Max(value = 8, message = "Semester must be between 1 and 8") Integer semester,
        @DecimalMin(value = "0.00", message = "Current CGPA must be between 0.00 and 10.00")
        @DecimalMax(value = "10.00", message = "Current CGPA must be between 0.00 and 10.00")
        @Digits(integer = 2, fraction = 2, message = "Current CGPA must have at most 2 decimal places") BigDecimal currentCgpa
) {}
