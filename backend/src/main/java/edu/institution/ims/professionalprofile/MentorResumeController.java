package edu.institution.ims.professionalprofile;

import edu.institution.ims.mentor.MentorStudentAssignmentRepository;
import edu.institution.ims.security.UserPrincipal;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Assigned mentors may read the same preserved resume and corrections as the student. */
@RestController
@RequestMapping("/api/v1/mentor/students/{studentId}")
@Transactional(readOnly=true)
public class MentorResumeController {
    private final MentorStudentAssignmentRepository assignments;
    private final ProfessionalProfileService profiles;
    private final ResumeAnalysisPersistenceService analysis;
    private final ResumeCorrectionService corrections;
    public MentorResumeController(MentorStudentAssignmentRepository assignments,ProfessionalProfileService profiles,
            ResumeAnalysisPersistenceService analysis,ResumeCorrectionService corrections) {
        this.assignments=assignments; this.profiles=profiles; this.analysis=analysis; this.corrections=corrections;
    }
    @GetMapping("/professional-profile")
    public ProfessionalProfileResponse profile(@PathVariable Long studentId,@AuthenticationPrincipal UserPrincipal principal) {
        assigned(studentId,principal); return profiles.getForAdmin(studentId);
    }
    @GetMapping("/resume/download")
    public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable Long studentId,@AuthenticationPrincipal UserPrincipal principal) {
        assigned(studentId,principal); return StudentProfessionalProfileController.file(profiles.downloadForAdmin(studentId));
    }
    @GetMapping("/resume/analysis")
    public ResumeAnalysisResponse analysis(@PathVariable Long studentId,@AuthenticationPrincipal UserPrincipal principal) {
        assigned(studentId,principal); return analysis.getOwn(studentId);
    }
    @GetMapping("/resume/analysis/corrections")
    public ResumeCorrectionService.Corrections corrections(@PathVariable Long studentId,@AuthenticationPrincipal UserPrincipal principal) {
        assigned(studentId,principal); return corrections.get(studentId);
    }
    private void assigned(Long studentId,UserPrincipal principal) {
        if (principal==null || !principal.active() || !"MENTOR".equals(principal.role())
                || assignments.findByStudentIdAndActiveTrue(studentId).filter(a -> a.getMentor().getId().equals(principal.id())
                    && a.getMentor().isActive()).isEmpty())
            throw new ResumeException(HttpStatus.NOT_FOUND,"ASSIGNED_RESUME_NOT_FOUND","Resume was not found in your assigned scope");
    }
}
