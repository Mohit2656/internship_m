package edu.institution.ims.studentprofile.dto;

import edu.institution.ims.studentprofile.ProfileStatus;

public record AdminStudentSummaryResponse(Long id, String loginEmail, String studentName, String uid, String rollNumber,
        String branch, String branchLabel, String instituteEmail, String phoneNumber, String academicYear, Integer semester,
        ProfileStatus profileStatus, String internshipStatus, String internshipSource, String internshipSourceLabel,
        int documentsCompleted, int documentsRequired, boolean diaryIssued) {}
