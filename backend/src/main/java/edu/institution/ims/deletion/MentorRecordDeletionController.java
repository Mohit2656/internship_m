package edu.institution.ims.deletion;

import edu.institution.ims.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mentor")
public class MentorRecordDeletionController {
    private final MentorRecordDeletionService service;
    public MentorRecordDeletionController(MentorRecordDeletionService service) { this.service=service; }
    public record DeletionReason(@NotBlank @Size(min=5,max=500) String reason) {}
    @DeleteMapping("/attendance/{id}")
    public ResponseEntity<Void> attendance(@AuthenticationPrincipal UserPrincipal user,@PathVariable Long id,@Valid @RequestBody DeletionReason request) {
        service.delete(user,id,false,request.reason()); return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/monthly-progress/{id}")
    public ResponseEntity<Void> monthly(@AuthenticationPrincipal UserPrincipal user,@PathVariable Long id,@Valid @RequestBody DeletionReason request) {
        service.delete(user,id,true,request.reason()); return ResponseEntity.noContent().build();
    }
}
