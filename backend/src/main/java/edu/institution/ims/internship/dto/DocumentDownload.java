package edu.institution.ims.internship.dto;

import org.springframework.core.io.Resource;

public record DocumentDownload(Resource resource, String originalFileName, String contentType, long fileSize) {}
