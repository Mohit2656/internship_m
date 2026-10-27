package edu.institution.ims.workplace;

import edu.institution.ims.common.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/workplace-locations/search")
public class WorkplaceLocationSearchController {
    private final WorkplaceLocationSearchService service;

    public WorkplaceLocationSearchController(WorkplaceLocationSearchService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public List<WorkplaceLocationSearchResult> search(@RequestParam(defaultValue = "") String q) {
        return service.search(q);
    }

    @ExceptionHandler(WorkplaceLocationSearchException.class)
    ResponseEntity<ApiError> searchFailure(WorkplaceLocationSearchException error) {
        var response = ResponseEntity.status(error.status());
        if (error.status() == HttpStatus.TOO_MANY_REQUESTS) response.header("Retry-After", "1");
        return response.body(new ApiError(Instant.now(), error.status().value(), error.code(), error.getMessage(), null));
    }
}
