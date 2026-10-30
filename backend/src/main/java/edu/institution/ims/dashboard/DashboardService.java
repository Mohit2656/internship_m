package edu.institution.ims.dashboard;

import edu.institution.ims.dashboard.dto.*;
import edu.institution.ims.internship.*;
import edu.institution.ims.user.Role;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static edu.institution.ims.dashboard.dto.DashboardSummaryResponse.*;

@Service
public class DashboardService {
    private static final List<WorkflowStatus> DOCUMENTS_PENDING = List.of(WorkflowStatus.INTERNSHIP_SECURED, WorkflowStatus.DOCUMENTS_PENDING);
    private static final List<WorkflowStatus> DOCUMENTS_COMPLETE = List.of(WorkflowStatus.DOCUMENTS_COMPLETE, WorkflowStatus.DIARY_ISSUED, WorkflowStatus.INTERNSHIP_DETAILS_COMPLETE);
    private static final List<WorkflowStatus> DIARY_ISSUED = List.of(WorkflowStatus.DIARY_ISSUED, WorkflowStatus.INTERNSHIP_DETAILS_COMPLETE);
    private final DashboardAnalyticsRepository analytics;

    public DashboardService(DashboardAnalyticsRepository analytics) { this.analytics = analytics; }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary() { return summary(null); }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(Long sessionId) {
        DashboardCountsProjection counts = analytics.counts(Role.STUDENT, DOCUMENTS_PENDING, DOCUMENTS_COMPLETE, DIARY_ISSUED,
                WorkflowStatus.INTERNSHIP_DETAILS_COMPLETE, WorkflowStatus.DOCUMENTS_COMPLETE, WorkflowStatus.DIARY_ISSUED, sessionId);
        return new DashboardSummaryResponse(
                new StudentOverview(zero(counts.getTotal()), zero(counts.getProfileComplete()), zero(counts.getProfileIncomplete())),
                new InternshipProgress(zero(counts.getInternshipSecured()), zero(counts.getInternshipNotSecured()), zero(counts.getDocumentsPending()),
                        zero(counts.getDocumentsComplete()), zero(counts.getDiaryIssued()), zero(counts.getDetailsComplete())),
                enumBreakdown(analytics.sourceCounts(Role.STUDENT, sessionId), InternshipSource.values()),
                enumBreakdown(analytics.companyTypeCounts(Role.STUDENT, sessionId), CompanyType.values()),
                enumBreakdown(analytics.modeCounts(Role.STUDENT, sessionId), InternshipMode.values()),
                stipend(sessionId), fullTimeEmployment(sessionId), topCompanies(sessionId),
                new Attention(zero(counts.getProfileIncomplete()), zero(counts.getInternshipNotSecured()), zero(counts.getDocumentsPending()),
                        zero(counts.getDiaryPending()), zero(counts.getDetailsPending())));
    }

    private Stipend stipend(Long sessionId) {
        StipendProjection projection = analytics.stipend(Role.STUDENT, sessionId);
        return new Stipend(zero(projection.getPaid()), zero(projection.getUnpaid()), projection.getAveragePaidMonthlyStipend());
    }
    private FullTimeEmployment fullTimeEmployment(Long sessionId) {
        Map<Boolean, Long> values = new HashMap<>();
        analytics.fullTimeEmploymentCounts(Role.STUDENT, sessionId).forEach(row -> values.put(row.getValue(), zero(row.getCount())));
        return new FullTimeEmployment(values.getOrDefault(true, 0L), values.getOrDefault(false, 0L));
    }
    private List<TopCompany> topCompanies(Long sessionId) {
        return analytics.topCompanies(Role.STUDENT, PageRequest.of(0, 5), sessionId).stream()
                .map(row -> new TopCompany(row.getCompanyName(), zero(row.getStudentCount()))).toList();
    }
    private static List<Breakdown> enumBreakdown(List<EnumCountProjection> rows, Enum<?>[] options) {
        Map<String, Long> values = new HashMap<>();
        rows.forEach(row -> values.put(row.getValue().name(), zero(row.getCount())));
        return Arrays.stream(options).map(option -> new Breakdown(option.name(), label(option), values.getOrDefault(option.name(), 0L))).toList();
    }
    private static String label(Enum<?> value) {
        if (value instanceof InternshipSource source) return source.getLabel();
        if (value instanceof CompanyType type) return type.getLabel();
        if (value instanceof InternshipMode mode) return mode.getLabel();
        return value.name();
    }
    private static long zero(Long value) { return value == null ? 0L : value; }
}
