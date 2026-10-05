package edu.institution.ims.attendance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record HybridScheduleRequest(
        @NotEmpty @Size(max = 7) List<@Valid HybridScheduleEntryRequest> entries
) {}
