package edu.institution.ims.professionalprofile;

import edu.institution.ims.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/student/resume/analysis")
public class StudentResumeAnalysisController {
    private final ResumeAnalysisService service;
    public StudentResumeAnalysisController(ResumeAnalysisService service) { this.service = service; }

    @GetMapping
    public ResumeAnalysisResponse get(@AuthenticationPrincipal UserPrincipal principal) { return service.getOwn(principal); }

    @PostMapping
    public ResumeAnalysisResponse analyze(@AuthenticationPrincipal UserPrincipal principal) { return service.analyzeOwn(principal); }
}
