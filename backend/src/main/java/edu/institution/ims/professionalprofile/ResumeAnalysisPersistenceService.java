package edu.institution.ims.professionalprofile;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ResumeAnalysisPersistenceService {
    private final StudentProfessionalProfileRepository profiles;
    private final StudentResumeRepository resumes;
    private final ResumeAnalysisRepository analyses;
    private final SkillSuggestionGenerator suggestionGenerator;

    public ResumeAnalysisPersistenceService(StudentProfessionalProfileRepository profiles, StudentResumeRepository resumes,
            ResumeAnalysisRepository analyses, SkillSuggestionGenerator suggestionGenerator) {
        this.profiles = profiles; this.resumes = resumes; this.analyses = analyses; this.suggestionGenerator = suggestionGenerator;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AnalysisCommand begin(Long studentId) {
        StudentProfessionalProfile profile = profiles.findByStudentIdForUpdate(studentId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "RESUME_NOT_FOUND", "Upload a resume before requesting analysis"));
        StudentResume resume = resumes.findByProfessionalProfileIdAndActiveTrue(profile.getId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "RESUME_NOT_FOUND", "Upload a resume before requesting analysis"));
        if (resume.getParsingStatus() == ResumeParsingStatus.PENDING)
            throw error(HttpStatus.CONFLICT, "ANALYSIS_IN_PROGRESS", "Resume analysis is already in progress");
        if (resume.getParsingStatus() == ResumeParsingStatus.PARSED)
            throw error(HttpStatus.CONFLICT, "ANALYSIS_ALREADY_COMPLETE", "This resume has already been analyzed");
        ResumeAnalysis analysis = analyses.findByResumeId(resume.getId()).orElseGet(() -> new ResumeAnalysis(resume));
        analysis.restart();
        resume.markAnalysisPending();
        resumes.save(resume);
        analyses.saveAndFlush(analysis);
        return new AnalysisCommand(resume.getId(), resume.getStorageKey(), resume.getOriginalFilename(), resume.getContentType());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResumeAnalysisResponse complete(Long resumeId, AiResumeResult result) {
        StudentResume resume = resumes.findById(resumeId).orElseThrow();
        ResumeAnalysis analysis = analyses.findByResumeId(resumeId).orElseThrow();
        if (analysis.getStatus() != ResumeParsingStatus.PENDING) throw new IllegalStateException("Resume analysis is not pending");
        analysis.complete(result);
        resume.markAnalysisParsed();
        resumes.save(resume);
        ResumeAnalysis saved = analyses.saveAndFlush(analysis);
        suggestionGenerator.generate(saved);
        return ResumeAnalysisResponse.from(saved);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long resumeId, String safeReason) {
        resumes.findById(resumeId).ifPresent(resume -> {
            analyses.findByResumeId(resumeId).ifPresent(analysis -> {
                if (analysis.getStatus() == ResumeParsingStatus.PENDING) {
                    analysis.fail(safeReason);
                    resume.markAnalysisFailed();
                    resumes.save(resume);
                    analyses.save(analysis);
                }
            });
        });
    }

    @Transactional(readOnly = true)
    public ResumeAnalysisResponse getOwn(Long studentId) {
        StudentProfessionalProfile profile = profiles.findByStudentId(studentId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "RESUME_NOT_FOUND", "Upload a resume before requesting analysis"));
        StudentResume resume = resumes.findByProfessionalProfileIdAndActiveTrue(profile.getId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "RESUME_NOT_FOUND", "Upload a resume before requesting analysis"));
        return analyses.findByResumeId(resume.getId()).map(ResumeAnalysisResponse::from)
                .orElseGet(() -> ResumeAnalysisResponse.notParsed(resume.getId()));
    }

    private static ResumeException error(HttpStatus status, String code, String message) {
        return new ResumeException(status, code, message);
    }
}
