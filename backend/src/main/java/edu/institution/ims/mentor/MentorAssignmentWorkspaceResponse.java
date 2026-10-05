package edu.institution.ims.mentor;

import java.util.List;

public record MentorAssignmentWorkspaceResponse(
        MentorAccountResponse mentor,
        List<AssignmentStudentResponse> assignedStudents,
        List<AssignmentStudentResponse> availableStudents,
        long totalStudents,
        long assignedStudentsCount,
        long unassignedStudentsCount
) {}
