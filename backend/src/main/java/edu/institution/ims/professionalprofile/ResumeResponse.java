package edu.institution.ims.professionalprofile;

import java.time.Instant;

public record ResumeResponse(Long id, String originalFilename, String contentType, long fileSize,
        Instant uploadedAt, boolean active, ResumeParsingStatus parsingStatus) {}
