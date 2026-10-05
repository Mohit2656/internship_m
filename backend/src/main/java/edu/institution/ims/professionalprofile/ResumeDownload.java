package edu.institution.ims.professionalprofile;

import org.springframework.core.io.Resource;

public record ResumeDownload(Resource resource, String originalFilename, String contentType, long fileSize) {}
