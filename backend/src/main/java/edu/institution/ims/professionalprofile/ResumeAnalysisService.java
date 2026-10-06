package edu.institution.ims.professionalprofile;

import edu.institution.ims.internship.storage.*;
import edu.institution.ims.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import java.sql.SQLException;
import java.util.*;

@Service
public class ResumeAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(ResumeAnalysisService.class);
    // Only fixed system messages are safe: HTTP/SQL exceptions can contain resume data or credentials.
    private static final Set<String> SAFE_FAILURE_MESSAGES = Set.of(
            "The stored document was not found", "The document could not be read", "Invalid document storage key",
            "AI service returned an empty response", "Resume AI service request failed",
            "Resume analysis is not pending", "No value present", "AI response value is too long",
            "AI response contains an invalid collection", "AI response contains an invalid experience",
            "AI response contains an empty experience", "AI response contains an invalid education entry",
            "AI response contains an empty education entry", "AI response contains invalid skills",
            "AI response contains invalid certifications", "AI response is missing modelName",
            "AI response is missing modelVersion", "AI response is missing skills", "AI response is missing certifications");
    private final ResumeAnalysisPersistenceService persistence;
    private final ResumeAiClient aiClient;
    private final FileStorageService storage;

    public ResumeAnalysisService(ResumeAnalysisPersistenceService persistence, ResumeAiClient aiClient, FileStorageService storage) {
        this.persistence = persistence; this.aiClient = aiClient; this.storage = storage;
    }

    public ResumeAnalysisResponse analyzeOwn(UserPrincipal principal) {
        AnalysisCommand command = persistence.begin(principal.id());
        String stage = "FILE_LOAD";
        try {
            StoredFile stored = storage.load(command.storageKey());
            stage = "AI_CALL";
            AiResumeResult response = aiClient.analyze(stored.resource(), command.originalFilename(), command.contentType());
            stage = "AI_RESPONSE_VALIDATION";
            AiResumeResult result = response.validated();
            stage = "RESULT_PERSISTENCE";
            return persistence.complete(command.resumeId(), result);
        } catch (ResumeAiClientException e) {
            logFailure(command.resumeId(), stage, e);
            persistence.fail(command.resumeId(), "AI_SERVICE_UNAVAILABLE");
            throw new ResumeException(HttpStatus.BAD_GATEWAY, "AI_SERVICE_UNAVAILABLE", "We couldn't analyze this resume. Please try again.");
        } catch (IllegalArgumentException e) {
            logFailure(command.resumeId(), stage, e);
            persistence.fail(command.resumeId(), "INVALID_AI_RESPONSE");
            throw new ResumeException(HttpStatus.BAD_GATEWAY, "INVALID_AI_RESPONSE", "We couldn't analyze this resume. Please try again.");
        } catch (RuntimeException e) {
            logFailure(command.resumeId(), stage, e);
            persistence.fail(command.resumeId(), "ANALYSIS_FAILED");
            throw new ResumeException(HttpStatus.BAD_GATEWAY, "RESUME_ANALYSIS_FAILED", "We couldn't analyze this resume. Please try again.");
        }
    }

    private static void logFailure(Long resumeId, String stage, RuntimeException error) {
        Throwable cause = error.getCause();
        StringBuilder trace = new StringBuilder();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = error;
        for (int depth = 0; current != null && depth < 8 && seen.add(current); depth++, current = current.getCause()) {
            trace.append(depth == 0 ? "" : "Caused by: ").append(current.getClass().getName())
                    .append(": ").append(safeMessage(current)).append('\n');
            StackTraceElement[] frames = current.getStackTrace();
            for (int i = 0; i < Math.min(frames.length, 32); i++) {
                StackTraceElement frame = frames[i];
                // Keep code locations only; omit raw messages, filenames, paths and module/loader names.
                trace.append("    at ").append(frame.getClassName()).append('.').append(frame.getMethodName())
                        .append(':').append(frame.getLineNumber()).append('\n');
            }
            if (frames.length > 32) trace.append("    [remaining frames omitted]\n");
        }
        if (current != null) trace.append("[remaining or circular causes omitted]\n");
        log.error("[resume-analysis] resumeId={} stage={} exceptionClass={} safeMessage={} causeClass={} causeMessage={}\n{}",
                resumeId, stage, error.getClass().getName(), safeMessage(error),
                cause == null ? "none" : cause.getClass().getName(), cause == null ? "none" : safeMessage(cause), trace);
    }

    private static String safeMessage(Throwable error) {
        if (error instanceof SQLException sql) {
            String state = sql.getSQLState();
            return "Database failure; SQLState=" + (state != null && state.matches("[A-Z0-9]{5}") ? state : "unknown");
        }
        if (error instanceof RestClientResponseException http) return "AI HTTP failure; status=" + http.getStatusCode().value();
        String message = error.getMessage();
        if (message == null) return "No exception message";
        return SAFE_FAILURE_MESSAGES.contains(message) ? message : "[exception message redacted]";
    }

    public ResumeAnalysisResponse getOwn(UserPrincipal principal) { return persistence.getOwn(principal.id()); }
}
