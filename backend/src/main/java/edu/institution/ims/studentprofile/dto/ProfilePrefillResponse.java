package edu.institution.ims.studentprofile.dto;

import java.util.List;

public record ProfilePrefillResponse(String studentName, String uid, String instituteEmail, String branch, String branchLabel,
        Integer semester, List<String> readOnlyFields) {}
