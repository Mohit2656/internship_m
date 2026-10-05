package edu.institution.ims.mentor;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/mentor-accounts")
public class AdminMentorAccountController {
    private final MentorAccountService service;
    public AdminMentorAccountController(MentorAccountService service) { this.service = service; }
    @GetMapping public List<MentorAccountResponse> list() { return service.list(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public MentorAccountResponse create(@Valid @RequestBody MentorAccountRequest request) { return service.create(request); }
}
