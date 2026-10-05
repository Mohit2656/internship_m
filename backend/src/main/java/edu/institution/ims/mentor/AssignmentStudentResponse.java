package edu.institution.ims.mentor;

import edu.institution.ims.studentprofile.ProfileStatus;

public record AssignmentStudentResponse(
        Long userId,
        String name,
        String uid,
        String branch,
        String branchLabel,
        Integer semester,
        ProfileStatus profileStatus,
        Long currentMentorUserId,
        String currentMentorName
) {}
