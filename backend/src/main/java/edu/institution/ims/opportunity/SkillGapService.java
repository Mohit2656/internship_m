package edu.institution.ims.opportunity;

import edu.institution.ims.professionalprofile.*;
import edu.institution.ims.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service
public class SkillGapService {
    private static final String INCOMPLETE = "No confirmed skills are available yet. Review your Professional Profile before using skill-gap analysis.";
    private static final String UNAVAILABLE = "Skill-gap analysis is temporarily unavailable.";
    private final OpportunityRepository opportunities;
    private final OpportunityMatchService matching;
    private final StudentProfessionalProfileRepository profiles;
    private final StudentSkillRepository skills;

    public SkillGapService(OpportunityRepository opportunities, OpportunityMatchService matching,
            StudentProfessionalProfileRepository profiles, StudentSkillRepository skills) {
        this.opportunities = opportunities; this.matching = matching; this.profiles = profiles; this.skills = skills;
    }

    @Transactional(readOnly = true)
    public StudentSkillGapResponse analyze(UserPrincipal principal) {
        StudentProfessionalProfile profile = profiles.findByStudentId(principal.id()).orElse(null);
        if (profile == null || skills.findAllByProfessionalProfileIdAndActiveTrueOrderByDisplayNameAsc(profile.getId()).isEmpty())
            return new StudentSkillGapResponse("INCOMPLETE_PROFILE", INCOMPLETE, 0, List.of(), List.of(), List.of());
        List<Opportunity> current = opportunities.findAllCurrentlyAvailable(OpportunityStatus.PUBLISHED, LocalDate.now());
        Map<Long, StudentOpportunityMatchResponse> results = matching.matchForStudentAcrossOpportunities(principal, current);
        if (results.values().stream().anyMatch(value -> !"AVAILABLE".equals(value.semanticStatus())))
            return new StudentSkillGapResponse("UNAVAILABLE", UNAVAILABLE, current.size(), List.of(), List.of(), List.of());

        Map<String, GapCount> gaps = new HashMap<>();
        Map<String, StrengthCount> strengths = new HashMap<>();
        Map<String, RelatedCount> related = new HashMap<>();
        results.values().forEach(value -> {
            if (value.evidence() == null) return;
            Set<String> exactSeen = new HashSet<>(); Set<String> relatedSeen = new HashSet<>();
            value.evidence().requiredSkills().forEach(match -> collect(match, true, gaps, strengths, related, exactSeen, relatedSeen));
            value.evidence().preferredSkills().forEach(match -> collect(match, false, gaps, strengths, related, exactSeen, relatedSeen));
        });
        List<StudentSkillGapResponse.Gap> gapValues = gaps.values().stream()
                .map(value -> new StudentSkillGapResponse.Gap(value.display, value.required, value.preferred))
                .sorted(Comparator.comparingInt(StudentSkillGapResponse.Gap::requiredByCount).reversed()
                        .thenComparing(StudentSkillGapResponse.Gap::preferredByCount, Comparator.reverseOrder())
                        .thenComparing(StudentSkillGapResponse.Gap::skill, String.CASE_INSENSITIVE_ORDER)).toList();
        List<StudentSkillGapResponse.Strength> strengthValues = strengths.values().stream()
                .map(value -> new StudentSkillGapResponse.Strength(value.display, value.count))
                .sorted(Comparator.comparingInt(StudentSkillGapResponse.Strength::exactMatchCount).reversed()
                        .thenComparing(StudentSkillGapResponse.Strength::skill, String.CASE_INSENSITIVE_ORDER)).toList();
        List<StudentSkillGapResponse.Related> relatedValues = related.values().stream()
                .map(value -> new StudentSkillGapResponse.Related(value.studentSkill, value.opportunitySkill, value.count))
                .sorted(Comparator.comparingInt(StudentSkillGapResponse.Related::opportunityCount).reversed()
                        .thenComparing(StudentSkillGapResponse.Related::opportunitySkill, String.CASE_INSENSITIVE_ORDER)).toList();
        return new StudentSkillGapResponse("AVAILABLE", null, current.size(), gapValues, strengthValues, relatedValues);
    }

    private static void collect(OpportunityMatchResult.SkillMatch match, boolean required, Map<String, GapCount> gaps,
            Map<String, StrengthCount> strengths, Map<String, RelatedCount> related, Set<String> exactSeen, Set<String> relatedSeen) {
        if ("MISSING".equals(match.matchType())) {
            String key = normalize(match.opportunitySkill()); GapCount count = gaps.computeIfAbsent(key, ignored -> new GapCount(match.opportunitySkill()));
            if (required) count.required++; else count.preferred++;
        } else if ("EXACT".equals(match.matchType())) {
            String key = normalize(match.studentSkill());
            if (exactSeen.add(key)) strengths.computeIfAbsent(key, ignored -> new StrengthCount(match.studentSkill())).count++;
        } else if ("RELATED".equals(match.matchType())) {
            String key = normalize(match.studentSkill()) + "\u0000" + normalize(match.opportunitySkill());
            if (relatedSeen.add(key)) related.computeIfAbsent(key, ignored -> new RelatedCount(match.studentSkill(), match.opportunitySkill())).count++;
        }
    }

    private static String normalize(String value) { return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }
    private static final class GapCount { final String display; int required; int preferred; GapCount(String display) { this.display = display; } }
    private static final class StrengthCount { final String display; int count; StrengthCount(String display) { this.display = display; } }
    private static final class RelatedCount { final String studentSkill; final String opportunitySkill; int count;
        RelatedCount(String studentSkill, String opportunitySkill) { this.studentSkill = studentSkill; this.opportunitySkill = opportunitySkill; } }
}
