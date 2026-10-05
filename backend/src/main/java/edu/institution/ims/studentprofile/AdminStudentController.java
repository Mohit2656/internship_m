package edu.institution.ims.studentprofile;

import edu.institution.ims.common.PagedResponse;
import edu.institution.ims.dashboard.AttentionCategory;
import edu.institution.ims.studentprofile.dto.*;
import edu.institution.ims.internship.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/admin/students")
public class AdminStudentController {
    private final AdminStudentService service;
    public AdminStudentController(AdminStudentService service) { this.service = service; }
    @GetMapping("/options") public ProfileOptionsResponse options() {
        var branches = Arrays.stream(Branch.values()).map(b -> new ProfileOptionsResponse.Option(b.name(), b.getLabel())).toList();
        return new ProfileOptionsResponse(branches, List.of("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"), 1, 8);
    }
    @GetMapping public PagedResponse<AdminStudentSummaryResponse> list(@RequestParam(required = false) String search,
            @RequestParam(required = false) Branch branch, @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semester, @RequestParam(required = false) WorkflowStatus internshipStatus,
            @RequestParam(required = false) InternshipSource internshipSource, @RequestParam(required = false) ProfileStatus profileStatus,
            @RequestParam(required = false) CompanyType companyType, @RequestParam(required = false) InternshipMode internshipMode,
            @RequestParam(required = false) Boolean paid, @RequestParam(required = false) Boolean fullTimeEmploymentOffered,
            @RequestParam(required = false) AttentionCategory attention, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(search, branch, academicYear, semester, internshipStatus, internshipSource, profileStatus, companyType,
                internshipMode, paid, fullTimeEmploymentOffered, attention, page, size);
    }
    @GetMapping("/{id}") public AdminStudentDetailResponse detail(@PathVariable Long id) { return service.detail(id); }
}
