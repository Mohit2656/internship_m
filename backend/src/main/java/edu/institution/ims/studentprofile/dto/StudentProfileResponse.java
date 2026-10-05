package edu.institution.ims.studentprofile.dto;

import edu.institution.ims.studentprofile.ProfileStatus;
import java.time.Instant;

public record StudentProfileResponse(Long id, String studentName, String branch, String branchLabel, String uid, String rollNumber,
        String permanentAddress, String currentAddress, String personalEmail, String instituteEmail, String phoneNumber,
        String guardianName, String guardianContact, String emergencyContact, String bloodGroup, String maskedAadhaar,
        String facultyMentorName, String facultyMentorContact, String internshipCoordinatorName, String internshipCoordinatorContact,
        String cdcCoordinatorName, String cdcCoordinatorContact, String academicYear, int semester, ProfileStatus profileStatus,
        Instant createdAt, Instant updatedAt) {}

