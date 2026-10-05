package edu.institution.ims.auth;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/v1/admin")
public class AdminVerificationController {
    @GetMapping("/verification") public Map<String, String> verification() { return Map.of("status", "authorized"); }
}
