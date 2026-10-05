package edu.institution.ims.internship.dto;

import java.time.Instant;
import java.util.List;

public record OnboardingResponse(Long id, String workflowStatus, String source, String sourceLabel,
        int documentsCompleted, int documentsRequired, List<DocumentResponse> documents, boolean diaryIssued,
        Instant securedAt, Instant documentsCompletedAt, Instant diaryIssuedAt, Instant createdAt, Instant updatedAt) {}
