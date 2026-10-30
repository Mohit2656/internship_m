package edu.institution.ims.opportunity;

import edu.institution.ims.notification.NotificationService;
import edu.institution.ims.professionalprofile.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;

@Service
public class OpportunityService {
    private final OpportunityRepository opportunities;
    private final OpportunitySkillRepository skills;
    private final UserRepository users;
    private final SkillNormalizer normalizer;
    private final NotificationService notifications;
    private final OpportunityMatchingService matching;

    public OpportunityService(OpportunityRepository opportunities, OpportunitySkillRepository skills,
            UserRepository users, SkillNormalizer normalizer, NotificationService notifications, OpportunityMatchingService matching) {
        this.opportunities = opportunities; this.skills = skills; this.users = users; this.normalizer = normalizer;
        this.notifications = notifications;
        this.matching = matching;
    }

    @Transactional
    public OpportunityResponse create(UserPrincipal principal, OpportunityRequest request) {
        validateRequest(request);
        User creator = users.findById(principal.id()).filter(user -> user.getRole() == Role.ADMIN)
                .orElseThrow(() -> error(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "Only the III Cell Incharge can manage opportunities"));
        Opportunity opportunity = opportunities.saveAndFlush(new Opportunity(creator, request));
        replaceSkills(opportunity, request);
        return response(opportunity);
    }

    @Transactional(readOnly = true)
    public List<OpportunityResponse> list() {
        return opportunities.findAllByOrderByUpdatedAtDesc().stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public OpportunityResponse get(Long id) { return response(find(id)); }

    @Transactional
    public OpportunityResponse update(Long id, OpportunityRequest request) {
        validateRequest(request);
        Opportunity opportunity = lock(id);
        if (opportunity.getStatus() == OpportunityStatus.CLOSED)
            throw error(HttpStatus.CONFLICT, "OPPORTUNITY_CLOSED", "Closed opportunities cannot be edited");
        opportunity.apply(request);
        if (opportunity.getStatus() == OpportunityStatus.PUBLISHED) validatePublishable(opportunity);
        opportunities.saveAndFlush(opportunity);
        replaceSkills(opportunity, request);
        return response(opportunity);
    }

    @Transactional
    public OpportunityResponse publish(Long id) {
        Opportunity opportunity = lock(id);
        if (opportunity.getStatus() == OpportunityStatus.CLOSED)
            throw error(HttpStatus.CONFLICT, "INVALID_OPPORTUNITY_TRANSITION", "A closed opportunity cannot be published");
        if (opportunity.getStatus() == OpportunityStatus.DRAFT) {
            validatePublishable(opportunity); opportunity.publish(); opportunities.saveAndFlush(opportunity);
            notifications.opportunityPublished(opportunity);
            matching.published(opportunity);
        }
        return response(opportunity);
    }

    @Transactional
    public OpportunityResponse close(Long id) {
        Opportunity opportunity = lock(id);
        if (opportunity.getStatus() == OpportunityStatus.DRAFT)
            throw error(HttpStatus.CONFLICT, "INVALID_OPPORTUNITY_TRANSITION", "Publish the opportunity before closing it");
        if (opportunity.getStatus() == OpportunityStatus.PUBLISHED) {
            opportunity.close(); opportunities.saveAndFlush(opportunity);
        }
        return response(opportunity);
    }

    private void replaceSkills(Opportunity opportunity, OpportunityRequest request) {
        List<NormalizedSkill> required = normalize(request.requiredSkills(), "required");
        List<NormalizedSkill> preferred = normalize(request.preferredSkills(), "preferred");
        skills.deleteAllByOpportunityId(opportunity.getId()); skills.flush();
        List<OpportunitySkill> replacements = new ArrayList<>();
        required.forEach(skill -> replacements.add(new OpportunitySkill(opportunity, skill, OpportunitySkillType.REQUIRED)));
        preferred.forEach(skill -> replacements.add(new OpportunitySkill(opportunity, skill, OpportunitySkillType.PREFERRED)));
        skills.saveAll(replacements); skills.flush();
    }

    private List<NormalizedSkill> normalize(List<String> values, String label) {
        List<NormalizedSkill> normalized = values.stream().map(normalizer::normalize).toList();
        Set<String> unique = new HashSet<>();
        for (NormalizedSkill skill : normalized) if (!unique.add(skill.normalizedName()))
            throw error(HttpStatus.BAD_REQUEST, "DUPLICATE_OPPORTUNITY_SKILL", "Duplicate " + label + " skill: " + skill.displayName());
        return normalized;
    }

    private void validateRequest(OpportunityRequest request) {
        if (new HashSet<>(request.eligibleBranches()).size() != request.eligibleBranches().size())
            throw error(HttpStatus.BAD_REQUEST, "DUPLICATE_ELIGIBLE_BRANCH", "Eligible branches must not contain duplicates");
        if (new HashSet<>(request.eligibleSemesters()).size() != request.eligibleSemesters().size())
            throw error(HttpStatus.BAD_REQUEST, "DUPLICATE_ELIGIBLE_SEMESTER", "Eligible semesters must not contain duplicates");
        String website = request.companyWebsite();
        if (website != null && !website.isBlank()) {
            try {
                URI uri = URI.create(website.trim());
                if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null)
                    throw new IllegalArgumentException();
            } catch (IllegalArgumentException ignored) {
                throw error(HttpStatus.BAD_REQUEST, "INVALID_COMPANY_WEBSITE", "Company website must be a valid HTTP or HTTPS URL");
            }
        }
        normalize(request.requiredSkills(), "required"); normalize(request.preferredSkills(), "preferred");
    }

    private void validatePublishable(Opportunity opportunity) {
        if (opportunity.getDescription() == null || opportunity.getDescription().isBlank())
            throw error(HttpStatus.CONFLICT, "OPPORTUNITY_NOT_READY", "Description is required before publication");
        if (opportunity.getInternshipMode() == null)
            throw error(HttpStatus.CONFLICT, "OPPORTUNITY_NOT_READY", "Internship mode is required before publication");
        if (opportunity.getApplicationDeadline() != null && opportunity.getApplicationDeadline().isBefore(LocalDate.now()))
            throw error(HttpStatus.CONFLICT, "OPPORTUNITY_NOT_READY", "Application deadline cannot be in the past at publication time");
    }

    private Opportunity find(Long id) {
        return opportunities.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "OPPORTUNITY_NOT_FOUND", "Opportunity was not found"));
    }
    private Opportunity lock(Long id) {
        return opportunities.findByIdForUpdate(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "OPPORTUNITY_NOT_FOUND", "Opportunity was not found"));
    }
    private OpportunityResponse response(Opportunity opportunity) {
        List<OpportunitySkill> allSkills = skills.findAllByOpportunityIdOrderByRequirementTypeAscDisplayNameAsc(opportunity.getId());
        List<OpportunityResponse.SkillResponse> required = allSkills.stream().filter(skill -> skill.getRequirementType() == OpportunitySkillType.REQUIRED).map(OpportunityResponse.SkillResponse::from).toList();
        List<OpportunityResponse.SkillResponse> preferred = allSkills.stream().filter(skill -> skill.getRequirementType() == OpportunitySkillType.PREFERRED).map(OpportunityResponse.SkillResponse::from).toList();
        return new OpportunityResponse(opportunity.getId(), opportunity.getCompanyName(), opportunity.getTitle(), opportunity.getDescription(),
                opportunity.getCompanyWebsite(), opportunity.getLocation(), opportunity.getInternshipMode(), opportunity.getDurationMonths(),
                opportunity.getStipendAmount(), opportunity.getCurrency(), opportunity.getApplicationDeadline(), opportunity.getMinimumCgpa(),
                opportunity.getStatus(), opportunity.getCreatedById(), opportunity.getPublishedAt(), opportunity.getClosedAt(),
                opportunity.getCreatedAt(), opportunity.getUpdatedAt(), opportunity.getEligibleBranches().stream().sorted().toList(),
                opportunity.getEligibleSemesters().stream().sorted().toList(), required, preferred);
    }
    private static OpportunityException error(HttpStatus status, String code, String message) { return new OpportunityException(status, code, message); }
}
