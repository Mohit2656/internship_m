package edu.institution.ims.professionalprofile;

import java.time.Instant;
import java.util.List;

public record ResumeAnalysisResponse(Long resumeId, ResumeParsingStatus status, String modelName, String modelVersion,
        Instant startedAt, Instant completedAt, List<String> skills, List<ExperienceResponse> experience,
        List<EducationResponse> education, List<String> certifications) {
    public record ExperienceResponse(String title, String company, String startDate, String endDate) {}
    public record EducationResponse(String degree, String field, String institution) {}

    static ResumeAnalysisResponse notParsed(Long resumeId) {
        return new ResumeAnalysisResponse(resumeId, ResumeParsingStatus.NOT_PARSED, null, null, null, null,
                List.of(), List.of(), List.of(), List.of());
    }

    static ResumeAnalysisResponse from(ResumeAnalysis analysis) {
        return new ResumeAnalysisResponse(analysis.getResume().getId(), analysis.getStatus(), analysis.getModelName(),
                analysis.getModelVersion(), analysis.getStartedAt(), analysis.getCompletedAt(),
                analysis.getSkills().stream().map(ResumeExtractedSkill::getName).toList(),
                analysis.getExperiences().stream().map(item -> new ExperienceResponse(item.getTitle(), item.getCompany(), item.getStartDate(), item.getEndDate())).toList(),
                analysis.getEducation().stream().map(item -> new EducationResponse(item.getDegree(), item.getField(), item.getInstitution())).toList(),
                analysis.getCertifications().stream().map(ResumeExtractedCertification::getName).toList());
    }
}
