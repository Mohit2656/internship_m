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
    @PostMapping("/{id}/close") public AcademicSessionService.Session close(@AuthenticationPrincipal UserPrincipal user,@PathVariable Long id,@RequestBody AcademicSessionService.Confirmation confirmation) { return service.close(user,id,confirmation); }
    @PostMapping("/{id}/reopen") public AcademicSessionService.Session reopen(@AuthenticationPrincipal UserPrincipal user,@PathVariable Long id,@RequestBody AcademicSessionService.Confirmation confirmation) { return service.reopen(user,id,confirmation); }
    @GetMapping("/{id}/deletion-preview") public AcademicSessionService.DeletionPreview deletionPreview(@PathVariable Long id) { return service.deletionPreview(id); }
    @DeleteMapping("/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal user,@PathVariable Long id,@RequestBody AcademicSessionService.Confirmation confirmation) { service.delete(user,id,confirmation); }
    @GetMapping("/{id}/history") public List<AcademicSessionService.Event> history(@PathVariable Long id) { return service.history(id); }
    @PutMapping("/{id}/dates") public AcademicSessionService.Session dateHistorical(@AuthenticationPrincipal UserPrincipal user,@PathVariable Long id,@RequestBody AcademicSessionService.Create request) { return service.dateHistorical(user,id,request); }
}
