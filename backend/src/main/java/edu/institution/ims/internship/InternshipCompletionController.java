package edu.institution.ims.internship;

import edu.institution.ims.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class InternshipCompletionController {
    private final InternshipCompletionService service;
    public InternshipCompletionController(InternshipCompletionService service) { this.service=service; }
    @GetMapping("/admin/students/{id}/internship-completion") public InternshipCompletionService.Completion get(@PathVariable Long id) { return service.forAdmin(id); }
    @PostMapping("/admin/students/{id}/internship-completion/complete") public InternshipCompletionService.Completion complete(@AuthenticationPrincipal UserPrincipal actor,@PathVariable Long id,@RequestBody InternshipCompletionService.Decision request) { return service.decide(actor,id,request,false); }
    @PostMapping("/admin/students/{id}/internship-completion/reopen") public InternshipCompletionService.Completion reopen(@AuthenticationPrincipal UserPrincipal actor,@PathVariable Long id,@RequestBody InternshipCompletionService.Decision request) { return service.decide(actor,id,request,true); }
    @GetMapping("/student/internship-completion") public InternshipCompletionService.Completion own(@AuthenticationPrincipal UserPrincipal user) { return service.own(user); }
    @GetMapping("/admin/internship-completions") public InternshipCompletionService.Page list(@RequestParam(required=false) Long sessionId,@RequestParam(required=false) String search,
            @RequestParam(required=false) String status,@RequestParam(required=false) String sort,@RequestParam(defaultValue="0") int page) { return service.list(sessionId,search,status,sort,page); }
}
