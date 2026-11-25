package edu.institution.ims.opportunity;

import edu.institution.ims.professionalprofile.*;
import edu.institution.ims.studentprofile.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class SystemMatchedStudentService {
    public record Candidate(Long studentId, String studentName, String uid, List<String> matchedSkills,
            List<String> missingRequiredSkills, double matchPercentage) {}
    public record Response(boolean available, String message, List<Candidate> students) {}

    private final OpportunityRepository opportunities;
    private final OpportunitySkillRepository opportunitySkills;
    private final StudentProfileRepository studentProfiles;
    private final StudentProfessionalProfileRepository professionalProfiles;
    private final StudentSkillRepository studentSkills;

    public SystemMatchedStudentService(OpportunityRepository opportunities, OpportunitySkillRepository opportunitySkills,
            StudentProfileRepository studentProfiles, StudentProfessionalProfileRepository professionalProfiles,
            StudentSkillRepository studentSkills) {
        this.opportunities = opportunities; this.opportunitySkills = opportunitySkills;
        this.studentProfiles = studentProfiles; this.professionalProfiles = professionalProfiles;
        this.studentSkills = studentSkills;
    }

    @Transactional(readOnly = true)
    public Response forOpportunity(Long opportunityId) {
        if (!opportunities.existsById(opportunityId))
            throw new OpportunityException(HttpStatus.NOT_FOUND, "OPPORTUNITY_NOT_FOUND", "Opportunity was not found");
        List<OpportunitySkill> required = opportunitySkills
                .findAllByOpportunityIdOrderByRequirementTypeAscDisplayNameAsc(opportunityId).stream()
                .filter(skill -> skill.getRequirementType() == OpportunitySkillType.REQUIRED).toList();
        if (required.isEmpty()) return new Response(false, "Skill match unavailable", List.of());

        List<StudentProfile> candidates = studentProfiles.findAllActiveStudentCandidates();
        List<Long> studentIds = candidates.stream().map(profile -> profile.getUser().getId()).toList();
        Map<Long, StudentProfessionalProfile> profilesByStudent = new HashMap<>();
        professionalProfiles.findAllByStudentIdIn(studentIds)
                .forEach(profile -> profilesByStudent.put(profile.getStudent().getId(), profile));
        Map<Long, Set<String>> skillsByProfile = new HashMap<>();
        List<Long> profileIds = profilesByStudent.values().stream().map(StudentProfessionalProfile::getId).toList();
        if (!profileIds.isEmpty()) {
            studentSkills.findAllByProfessionalProfileIdInAndActiveTrue(profileIds)
                    .forEach(skill -> skillsByProfile.computeIfAbsent(skill.getProfessionalProfile().getId(), ignored -> new HashSet<>())
                            .add(skill.getNormalizedName()));
        }

        List<Candidate> matched = new ArrayList<>();
        for (StudentProfile student : candidates) {
            StudentProfessionalProfile profile = profilesByStudent.get(student.getUser().getId());
            Set<String> owned = profile == null ? Set.of() : skillsByProfile.getOrDefault(profile.getId(), Set.of());
            List<String> present = required.stream().filter(skill -> owned.contains(skill.getNormalizedName()))
                    .map(OpportunitySkill::getDisplayName).toList();
            double percentage = present.size() * 100.0 / required.size();
            if (percentage < 50.0) continue;
            List<String> missing = required.stream().filter(skill -> !owned.contains(skill.getNormalizedName()))
                    .map(OpportunitySkill::getDisplayName).toList();
            matched.add(new Candidate(student.getUser().getId(), student.getStudentName(), student.getUid(),
                    present, missing, percentage));
        }
        matched.sort(Comparator.comparingDouble(Candidate::matchPercentage).reversed()
                .thenComparing(Candidate::studentName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Candidate::studentId));
        return new Response(true, null, List.copyOf(matched));
    }

    @Transactional(readOnly = true)
    public Map<Long, Double> matchPercentages(Long opportunityId, Collection<Long> studentIds) {
        List<OpportunitySkill> required = opportunitySkills
                .findAllByOpportunityIdOrderByRequirementTypeAscDisplayNameAsc(opportunityId).stream()
                .filter(skill -> skill.getRequirementType() == OpportunitySkillType.REQUIRED).toList();
        if (required.isEmpty() || studentIds.isEmpty()) return Map.of();
        Map<Long, StudentProfessionalProfile> profilesByStudent = new HashMap<>();
        professionalProfiles.findAllByStudentIdIn(studentIds)
                .forEach(profile -> profilesByStudent.put(profile.getStudent().getId(), profile));
        Map<Long, Set<String>> skillsByProfile = new HashMap<>();
        List<Long> profileIds = profilesByStudent.values().stream().map(StudentProfessionalProfile::getId).toList();
        if (!profileIds.isEmpty()) studentSkills.findAllByProfessionalProfileIdInAndActiveTrue(profileIds)
                .forEach(skill -> skillsByProfile.computeIfAbsent(skill.getProfessionalProfile().getId(), ignored -> new HashSet<>())
                        .add(skill.getNormalizedName()));
        Map<Long, Double> result = new HashMap<>();
        for (Long studentId : studentIds) {
            StudentProfessionalProfile profile = profilesByStudent.get(studentId);
            Set<String> owned = profile == null ? Set.of() : skillsByProfile.getOrDefault(profile.getId(), Set.of());
            long count = required.stream().filter(skill -> owned.contains(skill.getNormalizedName())).count();
            result.put(studentId, count * 100.0 / required.size());
        }
        return result;
    }
}
