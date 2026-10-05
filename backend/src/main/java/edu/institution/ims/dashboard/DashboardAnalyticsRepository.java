package edu.institution.ims.dashboard;

import edu.institution.ims.dashboard.dto.*;
import edu.institution.ims.internship.*;
import edu.institution.ims.user.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import java.util.*;

public interface DashboardAnalyticsRepository extends Repository<User, Long> {
    @Query("""
            select count(u.id) as total,
              sum(case when p.id is not null then 1 else 0 end) as profileComplete,
              sum(case when p.id is null then 1 else 0 end) as profileIncomplete,
              sum(case when o.id is not null then 1 else 0 end) as internshipSecured,
              sum(case when o.id is null then 1 else 0 end) as internshipNotSecured,
              sum(case when o.workflowStatus in :documentsPendingStatuses then 1 else 0 end) as documentsPending,
              sum(case when o.workflowStatus in :documentsCompleteStatuses then 1 else 0 end) as documentsComplete,
              sum(case when o.workflowStatus in :diaryIssuedStatuses then 1 else 0 end) as diaryIssued,
              sum(case when o.workflowStatus = :detailsCompleteStatus and d.id is not null then 1 else 0 end) as detailsComplete,
              sum(case when o.workflowStatus = :diaryPendingStatus then 1 else 0 end) as diaryPending,
              sum(case when o.workflowStatus = :detailsPendingStatus and d.id is null then 1 else 0 end) as detailsPending
            from User u left join u.studentProfile p left join u.internshipOnboarding o left join o.internshipDetails d
            where u.role = :role and u.active = true
            """)
    DashboardCountsProjection counts(Role role, Collection<WorkflowStatus> documentsPendingStatuses,
            Collection<WorkflowStatus> documentsCompleteStatuses, Collection<WorkflowStatus> diaryIssuedStatuses,
            WorkflowStatus detailsCompleteStatus, WorkflowStatus diaryPendingStatus, WorkflowStatus detailsPendingStatus);

    @Query("""
            select o.source as value, count(o.id) as count
            from InternshipOnboarding o join o.student u
            where u.role = :role and u.active = true and o.source is not null
            group by o.source
            """)
    List<EnumCountProjection> sourceCounts(Role role);

    @Query("""
            select d.companyType as value, count(d.id) as count
            from InternshipDetails d join d.onboarding o join o.student u
            where u.role = :role and u.active = true
            group by d.companyType
            """)
    List<EnumCountProjection> companyTypeCounts(Role role);

    @Query("""
            select d.internshipMode as value, count(d.id) as count
            from InternshipDetails d join d.onboarding o join o.student u
            where u.role = :role and u.active = true
            group by d.internshipMode
            """)
    List<EnumCountProjection> modeCounts(Role role);

    @Query("""
            select sum(case when d.stipendPerMonth > 0 then 1 else 0 end) as paid,
              sum(case when d.stipendPerMonth = 0 then 1 else 0 end) as unpaid,
              avg(case when d.stipendPerMonth > 0 then d.stipendPerMonth else null end) as averagePaidMonthlyStipend
            from InternshipDetails d join d.onboarding o join o.student u
            where u.role = :role and u.active = true
            """)
    StipendProjection stipend(Role role);

    @Query("""
            select d.fullTimeEmploymentOffered as value, count(d.id) as count
            from InternshipDetails d join d.onboarding o join o.student u
            where u.role = :role and u.active = true
            group by d.fullTimeEmploymentOffered
            """)
    List<BooleanCountProjection> fullTimeEmploymentCounts(Role role);

    @Query("""
            select min(trim(d.companyName)) as companyName, count(d.id) as studentCount
            from InternshipDetails d join d.onboarding o join o.student u
            where u.role = :role and u.active = true
            group by upper(trim(d.companyName))
            order by count(d.id) desc, min(trim(d.companyName)) asc
            """)
    List<TopCompanyProjection> topCompanies(Role role, Pageable pageable);
}
