package edu.institution.ims.professionalprofile;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/students/{studentId}")
public class AdminProfessionalProfileController {
    private final ProfessionalProfileService service;
    private final StudentSkillService skills;

    public AdminProfessionalProfileController(ProfessionalProfileService service, StudentSkillService skills) { this.service = service; this.skills = skills; }

    @GetMapping("/professional-profile")
    public ProfessionalProfileResponse profile(@PathVariable Long studentId) {
        return service.getForAdmin(studentId);
    }

    @GetMapping("/professional-profile/skills")
    public java.util.List<SkillProfileResponse.SkillResponse> confirmedSkills(@PathVariable Long studentId) {
        return skills.confirmedForStudent(studentId);
    }

    @GetMapping("/resume/download")
    public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable Long studentId) {
        return StudentProfessionalProfileController.file(service.downloadForAdmin(studentId));
    }
}
