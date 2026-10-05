package edu.institution.ims.opportunity;

import edu.institution.ims.professionalprofile.SkillNormalizer;
import edu.institution.ims.studentprofile.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class AdminTalentDiscoveryService {
    static final String ORDERING = "Eligibility (eligible, unknown, ineligible), then exact required skills, related required skills, exact preferred skills, and student name.";
    private final OpportunityRepository opportunities;
    private final StudentProfileRepository studentProfiles;
    private final OpportunityMatchService matching;
    private final SkillNormalizer skillNormalizer;

    public AdminTalentDiscoveryService(OpportunityRepository opportunities, StudentProfileRepository studentProfiles,
            OpportunityMatchService matching, SkillNormalizer skillNormalizer) {
        this.opportunities = opportunities;
        this.studentProfiles = studentProfiles;
        this.matching = matching;
        this.skillNormalizer = skillNormalizer;
    }

    @Transactional(readOnly = true)
    public TalentDiscoveryResponse discover(Long opportunityId, String search, TalentEligibilityFilter eligibility,
            Branch branch, Integer semester, String skill, int page, int size) {
        if (page < 0 || size < 1 || size > 50 || semester != null && (semester < 1 || semester > 12))
            throw new OpportunityException(HttpStatus.BAD_REQUEST, "INVALID_FILTER", "Use a non-negative page, size from 1 to 50, and a valid semester");
        Opportunity opportunity = activeOpportunity(opportunityId);

        String cleanSearch = clean(search);
        List<StudentProfile> candidates = studentProfiles.findAllActiveStudentCandidates().stream()
                .filter(profile -> cleanSearch == null || contains(profile.getStudentName(), cleanSearch) || contains(profile.getUid(), cleanSearch))
                .filter(profile -> branch == null || profile.getBranch() == branch)
                .filter(profile -> semester == null || profile.getSemester() == semester)
                .toList();
        Map<Long, StudentOpportunityMatchResponse> results = matching.matchForAdmin(opportunity, candidates);
        String normalizedSkill = clean(skill) == null ? null : skillNormalizer.normalize(skill).normalizedName();
        List<TalentDiscoveryResponse.Candidate> ordered = candidates.stream()
                .map(profile -> candidate(profile, results.get(profile.getUser().getId())))
                .filter(candidate -> matchesEligibility(candidate.alignment().eligibility().overall(), eligibility))
                .filter(candidate -> normalizedSkill == null || hasRelevantSkill(candidate.alignment(), normalizedSkill))
                .sorted(candidateOrder())
                .toList();
        int from = (int) Math.min((long) page * size, ordered.size());
        int to = Math.min(from + size, ordered.size());
        int totalPages = ordered.isEmpty() ? 0 : (ordered.size() + size - 1) / size;
        return new TalentDiscoveryResponse(
                new TalentDiscoveryResponse.OpportunitySummary(opportunity.getId(), opportunity.getCompanyName(), opportunity.getTitle(), opportunity.getStatus()),
                ORDERING, page, size, ordered.size(), totalPages, ordered.subList(from, to));
    }

    @Transactional(readOnly = true)
    public AdminSkillGapResponse skillGaps(Long opportunityId, String search, TalentEligibilityFilter eligibility,
            Branch branch, Integer semester, String skill) {
        if (semester != null && (semester < 1 || semester > 12))
            throw new OpportunityException(HttpStatus.BAD_REQUEST, "INVALID_FILTER", "Use a valid semester");
        Opportunity opportunity = activeOpportunity(opportunityId);
        String cleanSearch = clean(search);
        List<StudentProfile> candidates = studentProfiles.findAllActiveStudentCandidates().stream()
                .filter(profile -> cleanSearch == null || contains(profile.getStudentName(), cleanSearch) || contains(profile.getUid(), cleanSearch))
                .filter(profile -> branch == null || profile.getBranch() == branch)
                .filter(profile -> semester == null || profile.getSemester() == semester)
                .toList();
        Map<Long, StudentOpportunityMatchResponse> results = matching.matchForAdmin(opportunity, candidates);
        String normalizedSkill = clean(skill) == null ? null : skillNormalizer.normalize(skill).normalizedName();
        List<StudentOpportunityMatchResponse> eligiblePopulation = candidates.stream()
                .map(profile -> results.get(profile.getUser().getId()))
                .filter(Objects::nonNull)
                .filter(result -> matchesEligibility(result.eligibility().overall(), eligibility))
                .toList();
        if (eligiblePopulation.stream().anyMatch(result -> !"AVAILABLE".equals(result.semanticStatus())))
            return new AdminSkillGapResponse("UNAVAILABLE", "Skill-gap analysis is temporarily unavailable.", eligiblePopulation.size(), List.of(), List.of());
        List<StudentOpportunityMatchResponse> included = eligiblePopulation.stream()
                .filter(result -> normalizedSkill == null || hasRelevantSkill(result, normalizedSkill)).toList();
        Map<String, AdminGapCount> required = new HashMap<>(); Map<String, AdminGapCount> preferred = new HashMap<>();
        included.forEach(result -> {
            if (result.evidence() == null) return;
            result.evidence().requiredSkills().stream().filter(match -> "MISSING".equals(match.matchType()))
                    .forEach(match -> increment(required, match.opportunitySkill()));
            result.evidence().preferredSkills().stream().filter(match -> "MISSING".equals(match.matchType()))
                    .forEach(match -> increment(preferred, match.opportunitySkill()));
        });
        return new AdminSkillGapResponse("AVAILABLE", null, included.size(), gapValues(required), gapValues(preferred));
    }

    private Opportunity activeOpportunity(Long opportunityId) {
        Opportunity opportunity = opportunities.findById(opportunityId)
                .orElseThrow(() -> new OpportunityException(HttpStatus.NOT_FOUND, "OPPORTUNITY_NOT_FOUND", "Opportunity was not found"));
        if (opportunity.getStatus() == OpportunityStatus.CLOSED)
            throw new OpportunityException(HttpStatus.CONFLICT, "OPPORTUNITY_CLOSED", "Talent discovery is not available for a closed opportunity");
        return opportunity;
    }

    private static void increment(Map<String, AdminGapCount> values, String skill) {
        values.computeIfAbsent(simpleNormalize(skill), ignored -> new AdminGapCount(skill)).count++;
    }
    private static List<AdminSkillGapResponse.Gap> gapValues(Map<String, AdminGapCount> values) {
        return values.values().stream().map(value -> new AdminSkillGapResponse.Gap(value.display, value.count))
                .sorted(Comparator.comparingInt(AdminSkillGapResponse.Gap::studentsMissing).reversed()
                        .thenComparing(AdminSkillGapResponse.Gap::skill, String.CASE_INSENSITIVE_ORDER)).toList();
    }
    private static final class AdminGapCount { final String display; int count; AdminGapCount(String display) { this.display = display; } }

    private static TalentDiscoveryResponse.Candidate candidate(StudentProfile profile, StudentOpportunityMatchResponse alignment) {
        long exactRequired = count(alignment, true, "EXACT");
        long relatedRequired = count(alignment, true, "RELATED");
        long exactPreferred = count(alignment, false, "EXACT");
        return new TalentDiscoveryResponse.Candidate(profile.getUser().getId(), profile.getStudentName(), profile.getUid(), profile.getBranch(),
                profile.getSemester(), Math.toIntExact(exactRequired), Math.toIntExact(relatedRequired), Math.toIntExact(exactPreferred), alignment);
    }

    private static long count(StudentOpportunityMatchResponse alignment, boolean required, String matchType) {
        if (alignment == null || alignment.evidence() == null) return 0;
        List<OpportunityMatchResult.SkillMatch> values = required ? alignment.evidence().requiredSkills() : alignment.evidence().preferredSkills();
        return values.stream().filter(match -> matchType.equals(match.matchType())).count();
    }

    private static boolean hasRelevantSkill(StudentOpportunityMatchResponse alignment, String normalizedSkill) {
        if (alignment == null || alignment.evidence() == null) return false;
        return java.util.stream.Stream.concat(alignment.evidence().requiredSkills().stream(), alignment.evidence().preferredSkills().stream())
                .filter(match -> !"MISSING".equals(match.matchType()))
                .map(OpportunityMatchResult.SkillMatch::studentSkill)
                .filter(Objects::nonNull)
                .map(AdminTalentDiscoveryService::simpleNormalize)
                .anyMatch(normalizedSkill::equals);
    }

    private static boolean matchesEligibility(String actual, TalentEligibilityFilter filter) {
        TalentEligibilityFilter effective = filter == null ? TalentEligibilityFilter.ELIGIBLE_OR_UNKNOWN : filter;
        return switch (effective) {
            case ELIGIBLE_OR_UNKNOWN -> !"INELIGIBLE".equals(actual);
            case ALL -> true;
            default -> effective.name().equals(actual);
        };
    }

    private static Comparator<TalentDiscoveryResponse.Candidate> candidateOrder() {
        return Comparator.comparingInt((TalentDiscoveryResponse.Candidate value) -> eligibilityRank(value.alignment().eligibility().overall()))
                .thenComparing(TalentDiscoveryResponse.Candidate::exactRequiredSkills, Comparator.reverseOrder())
                .thenComparing(TalentDiscoveryResponse.Candidate::relatedRequiredSkills, Comparator.reverseOrder())
                .thenComparing(TalentDiscoveryResponse.Candidate::exactPreferredSkills, Comparator.reverseOrder())
                .thenComparing(TalentDiscoveryResponse.Candidate::studentName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(TalentDiscoveryResponse.Candidate::uid, String.CASE_INSENSITIVE_ORDER);
    }

    private static int eligibilityRank(String value) {
        return switch (value) { case "ELIGIBLE" -> 0; case "UNKNOWN" -> 1; default -> 2; };
    }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT); }
    private static boolean contains(String value, String search) { return value != null && value.toLowerCase(Locale.ROOT).contains(search); }
    private static String simpleNormalize(String value) { return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }
}
