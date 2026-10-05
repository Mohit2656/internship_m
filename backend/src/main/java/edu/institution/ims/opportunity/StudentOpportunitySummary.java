package edu.institution.ims.opportunity;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record StudentOpportunitySummary(Long id, String companyName, String title, OpportunityMode mode, String location,
        BigDecimal stipendAmount, String currency, LocalDate applicationDeadline, Instant publishedAt,
        List<String> requiredSkills) {}
