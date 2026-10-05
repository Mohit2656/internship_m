package edu.institution.ims.opportunity;

import edu.institution.ims.common.PagedResponse;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StudentOpportunityService {
    private final OpportunityRepository opportunities;
    private final OpportunitySkillRepository skills;

    public StudentOpportunityService(OpportunityRepository opportunities, OpportunitySkillRepository skills) {
        this.opportunities = opportunities; this.skills = skills;
    }

    @Transactional(readOnly = true)
    public PagedResponse<StudentOpportunitySummary> list(String search, OpportunityMode mode, StudentOpportunitySort sort, int page, int size) {
        int safePage = Math.max(0, page); int safeSize = Math.min(50, Math.max(1, size));
        Sort ordering = sort == StudentOpportunitySort.DEADLINE_SOON
                ? Sort.by(Sort.Order.asc("applicationDeadline").nullsLast(), Sort.Order.desc("publishedAt"), Sort.Order.desc("id"))
                : Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));
        String query = search == null || search.isBlank() ? null : search.trim();
        Page<Opportunity> result = opportunities.findActiveForStudents(OpportunityStatus.PUBLISHED, LocalDate.now(), query, mode,
                PageRequest.of(safePage, safeSize, ordering));
        List<Long> ids = result.getContent().stream().map(Opportunity::getId).toList();
        Map<Long, List<String>> requiredSkills = ids.isEmpty() ? Map.of() : skills
                .findAllByOpportunityIdInAndRequirementTypeOrderByDisplayNameAsc(ids, OpportunitySkillType.REQUIRED).stream()
                .collect(Collectors.groupingBy(OpportunitySkill::getParentOpportunityId, LinkedHashMap::new,
                        Collectors.mapping(OpportunitySkill::getDisplayName, Collectors.toList())));
        return PagedResponse.from(result.map(opportunity -> summary(opportunity, requiredSkills.getOrDefault(opportunity.getId(), List.of()))));
    }

    @Transactional(readOnly = true)
    public StudentOpportunityDetail detail(Long id) {
        Opportunity opportunity = opportunities.findActiveForStudentById(id, OpportunityStatus.PUBLISHED, LocalDate.now())
                .orElseThrow(() -> new OpportunityException(HttpStatus.NOT_FOUND, "OPPORTUNITY_NOT_FOUND", "Active opportunity was not found"));
        List<OpportunitySkill> allSkills = skills.findAllByOpportunityIdOrderByRequirementTypeAscDisplayNameAsc(id);
        List<String> required = allSkills.stream().filter(skill -> skill.getRequirementType() == OpportunitySkillType.REQUIRED).map(OpportunitySkill::getDisplayName).toList();
        List<String> preferred = allSkills.stream().filter(skill -> skill.getRequirementType() == OpportunitySkillType.PREFERRED).map(OpportunitySkill::getDisplayName).toList();
        return new StudentOpportunityDetail(opportunity.getId(), opportunity.getCompanyName(), opportunity.getTitle(),
                opportunity.getDescription(), opportunity.getCompanyWebsite(), opportunity.getInternshipMode(), opportunity.getLocation(),
                opportunity.getDurationMonths(), opportunity.getStipendAmount(), opportunity.getCurrency(), opportunity.getApplicationDeadline(),
                opportunity.getMinimumCgpa(), opportunity.getEligibleBranches().stream().sorted().toList(),
                opportunity.getEligibleSemesters().stream().sorted().toList(), required, preferred);
    }

    private static StudentOpportunitySummary summary(Opportunity opportunity, List<String> requiredSkills) {
        return new StudentOpportunitySummary(opportunity.getId(), opportunity.getCompanyName(), opportunity.getTitle(),
                opportunity.getInternshipMode(), opportunity.getLocation(), opportunity.getStipendAmount(), opportunity.getCurrency(),
                opportunity.getApplicationDeadline(), opportunity.getPublishedAt(), requiredSkills);
    }
}
