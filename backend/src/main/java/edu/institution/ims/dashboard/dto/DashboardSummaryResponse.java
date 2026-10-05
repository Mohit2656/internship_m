package edu.institution.ims.dashboard.dto;

import java.math.BigDecimal;
import java.util.*;

public record DashboardSummaryResponse(
        StudentOverview students, InternshipProgress internshipProgress, List<Breakdown> sources,
        List<Breakdown> companyTypes, List<Breakdown> internshipModes, Stipend stipend,
        FullTimeEmployment fullTimeEmployment, List<TopCompany> topCompanies, Attention attention) {
    public record StudentOverview(long total, long profileComplete, long profileIncomplete) {}
    public record InternshipProgress(long secured, long notSecured, long documentsPending, long documentsComplete,
            long diaryIssued, long detailsComplete) {}
    public record Breakdown(String value, String label, long count) {}
    public record Stipend(long paid, long unpaid, BigDecimal averagePaidMonthlyStipend) {}
    public record FullTimeEmployment(long yes, long no) {}
    public record TopCompany(String companyName, long students) {}
    public record Attention(long profileIncomplete, long internshipNotSecured, long documentsPending,
            long diaryPending, long internshipDetailsPending) {}
}
