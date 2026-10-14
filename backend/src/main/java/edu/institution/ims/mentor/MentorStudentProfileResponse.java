package edu.institution.ims.mentor;

import java.util.List;

public record MentorStudentProfileResponse(
        String studentName, String uid, String branch, String branchLabel, String rollNumber,
        String instituteEmail, String personalEmail, String phoneNumber, String academicYear, int semester,
        String professionalHeadline, String careerInterests, List<String> confirmedSkills,
        java.time.Instant profileUpdatedAt, java.time.Instant professionalUpdatedAt, java.time.Instant skillsUpdatedAt) {}
