package edu.institution.ims.internship.dto;

import java.time.Instant;

public record DocumentResponse(Long id, String documentType, String documentTypeLabel, boolean uploaded,
        String originalFileName, String contentType, Long fileSize, Instant uploadedAt) {}
