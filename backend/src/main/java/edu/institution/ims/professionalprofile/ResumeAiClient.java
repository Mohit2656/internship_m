package edu.institution.ims.professionalprofile;

import org.springframework.core.io.Resource;

public interface ResumeAiClient {
    AiResumeResult analyze(Resource file, String filename, String contentType);
}
