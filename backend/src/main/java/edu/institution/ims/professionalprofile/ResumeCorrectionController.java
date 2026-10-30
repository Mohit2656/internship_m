package edu.institution.ims.professionalprofile;

import edu.institution.ims.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ResumeCorrectionController {
    private final ResumeCorrectionService service;
    private final ResumeAnalysisPersistenceService analysis;
    public ResumeCorrectionController(ResumeCorrectionService service, ResumeAnalysisPersistenceService analysis) {
        this.service=service; this.analysis=analysis;
    }
    @GetMapping("/api/v1/student/resume/analysis/corrections")
    public ResumeCorrectionService.Corrections own(@AuthenticationPrincipal UserPrincipal user) { return service.get(user.id()); }
    @PutMapping("/api/v1/student/resume/analysis/corrections")
    public ResumeCorrectionService.Corrections saveOwn(@AuthenticationPrincipal UserPrincipal user,@RequestBody ResumeCorrectionService.Save body) {
        return service.save(user.id(),user,body);
    }
    @GetMapping("/api/v1/admin/students/{studentId}/resume/analysis")
    public ResumeAnalysisResponse analysis(@PathVariable Long studentId) { return analysis.getOwn(studentId); }
    @GetMapping("/api/v1/admin/students/{studentId}/resume/analysis/corrections")
    public ResumeCorrectionService.Corrections get(@PathVariable Long studentId) { return service.get(studentId); }
    @PutMapping("/api/v1/admin/students/{studentId}/resume/analysis/corrections")
    public ResumeCorrectionService.Corrections save(@PathVariable Long studentId,@AuthenticationPrincipal UserPrincipal user,
            @RequestBody ResumeCorrectionService.Save body) { return service.save(studentId,user,body); }
}
