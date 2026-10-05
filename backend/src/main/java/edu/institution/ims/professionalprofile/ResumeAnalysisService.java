package edu.institution.ims.professionalprofile;

import edu.institution.ims.internship.storage.*;
import edu.institution.ims.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ResumeAnalysisService {
    private final ResumeAnalysisPersistenceService persistence;
    private final ResumeAiClient aiClient;
    private final FileStorageService storage;

    public ResumeAnalysisService(ResumeAnalysisPersistenceService persistence, ResumeAiClient aiClient, FileStorageService storage) {
        this.persistence = persistence; this.aiClient = aiClient; this.storage = storage;
    }

    public ResumeAnalysisResponse analyzeOwn(UserPrincipal principal) {
        AnalysisCommand command = persistence.begin(principal.id());
        try {
            StoredFile stored = storage.load(command.storageKey());
            AiResumeResult result = aiClient.analyze(stored.resource(), command.originalFilename(), command.contentType()).validated();
            return persistence.complete(command.resumeId(), result);
        } catch (ResumeAiClientException e) {
            persistence.fail(command.resumeId(), "AI_SERVICE_UNAVAILABLE");
            throw new ResumeException(HttpStatus.BAD_GATEWAY, "AI_SERVICE_UNAVAILABLE", "We couldn't analyze this resume. Please try again.");
        } catch (IllegalArgumentException e) {
            persistence.fail(command.resumeId(), "INVALID_AI_RESPONSE");
            throw new ResumeException(HttpStatus.BAD_GATEWAY, "INVALID_AI_RESPONSE", "We couldn't analyze this resume. Please try again.");
        } catch (RuntimeException e) {
            persistence.fail(command.resumeId(), "ANALYSIS_FAILED");
            throw new ResumeException(HttpStatus.BAD_GATEWAY, "RESUME_ANALYSIS_FAILED", "We couldn't analyze this resume. Please try again.");
        }
    }

    public ResumeAnalysisResponse getOwn(UserPrincipal principal) { return persistence.getOwn(principal.id()); }
}
