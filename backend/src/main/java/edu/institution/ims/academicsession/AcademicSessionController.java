package edu.institution.ims.academicsession;

import edu.institution.ims.security.UserPrincipal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/academic-sessions") @PreAuthorize("hasRole('ADMIN')")
public class AcademicSessionController {
    private final AcademicSessionService service;
    public AcademicSessionController(AcademicSessionService service) { this.service=service; }
    @GetMapping public List<AcademicSessionService.Session> list() { return service.list(); }
    @PostMapping public AcademicSessionService.Session create(@AuthenticationPrincipal UserPrincipal user,@RequestBody AcademicSessionService.Create request) { return service.create(user,request); }
    @PostMapping("/{id}/activate") public AcademicSessionService.Session activate(@AuthenticationPrincipal UserPrincipal user,@PathVariable Long id) { return service.activate(user,id); }
    @PutMapping("/{id}/dates") public AcademicSessionService.Session dateHistorical(@AuthenticationPrincipal UserPrincipal user,@PathVariable Long id,@RequestBody AcademicSessionService.Create request) { return service.dateHistorical(user,id,request); }
}
