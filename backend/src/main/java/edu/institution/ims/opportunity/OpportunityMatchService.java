package edu.institution.ims.opportunity;

import edu.institution.ims.professionalprofile.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.studentprofile.StudentProfile;
import edu.institution.ims.studentprofile.StudentProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service
public class OpportunityMatchService {
    private final OpportunityRepository opportunities; private final OpportunitySkillRepository opportunitySkills;
    private final StudentProfileRepository studentProfiles; private final StudentProfessionalProfileRepository professionalProfiles;
    private final StudentSkillRepository studentSkills; private final StudentResumeRepository resumes;
    private final ResumeAnalysisRepository resumeAnalyses; private final OpportunityAiAnalysisRepository opportunityAnalyses;
    private final OpportunityMatchClient client;

    public OpportunityMatchService(OpportunityRepository opportunities, OpportunitySkillRepository opportunitySkills,
            StudentProfileRepository studentProfiles, StudentProfessionalProfileRepository professionalProfiles,
            StudentSkillRepository studentSkills, StudentResumeRepository resumes, ResumeAnalysisRepository resumeAnalyses,
            OpportunityAiAnalysisRepository opportunityAnalyses, OpportunityMatchClient client) {
        this.opportunities = opportunities; this.opportunitySkills = opportunitySkills; this.studentProfiles = studentProfiles;
        this.professionalProfiles = professionalProfiles; this.studentSkills = studentSkills; this.resumes = resumes;
        this.resumeAnalyses = resumeAnalyses; this.opportunityAnalyses = opportunityAnalyses; this.client = client;
    }

    @Transactional(readOnly = true)
    public StudentOpportunityMatchResponse match(UserPrincipal principal, Long opportunityId) {
        Opportunity opportunity = opportunities.findActiveForStudentById(opportunityId, OpportunityStatus.PUBLISHED, LocalDate.now())
                .orElseThrow(() -> new OpportunityException(HttpStatus.NOT_FOUND, "OPPORTUNITY_NOT_FOUND", "Active opportunity was not found"));
        StudentProfile institutional = studentProfiles.findByUserId(principal.id()).orElse(null);
        StudentOpportunityMatchResponse.Eligibility eligibility = eligibility(opportunity, institutional);
        StudentProfessionalProfile professional = professionalProfiles.findByStudentId(principal.id()).orElse(null);
        List<StudentSkill> activeSkills = professional == null ? List.of() : studentSkills.findAllByProfessionalProfileIdAndActiveTrueOrderByDisplayNameAsc(professional.getId());
        List<String> experience = currentExperience(professional);
        List<OpportunitySkill> authoritative = opportunitySkills.findAllByOpportunityIdOrderByRequirementTypeAscDisplayNameAsc(opportunityId);
        List<String> roles = new ArrayList<>(); List<String> responsibilities = new ArrayList<>();
        opportunityAnalyses.findFirstByOpportunityIdOrderByCreatedAtDesc(opportunityId).filter(a -> a.getStatus() == OpportunityAiAnalysisStatus.ANALYZED)
                .filter(a -> a.getInputHash().equals(OpportunityAiFingerprint.of(opportunity))).ifPresent(a -> a.getSignals().forEach(signal -> {
                    if (signal.getSignalType() == OpportunityAiSignalType.ROLE) roles.add(signal.getDisplayValue());
                    else responsibilities.add(signal.getDisplayValue());
                }));
        OpportunityMatchRequest request = request(opportunity, professional, activeSkills, experience, authoritative, roles, responsibilities);
        String notice = activeSkills.isEmpty() ? "No confirmed skills are currently available for semantic skill comparison." : null;
        try {
            OpportunityMatchResult result = client.match(request);
            return available(opportunityId, eligibility, request, result, notice);
        } catch (OpportunityMatchClientException e) {
            return unavailable(opportunityId, eligibility, notice);
        }
    }

    /**
     * Builds the same canonical Phase 4.7 result for a set of administrator-visible
     * candidates, while loading student evidence in bulk and invoking the AI service once.
     */
    @Transactional(readOnly = true)
    public Map<Long, StudentOpportunityMatchResponse> matchForAdmin(Opportunity opportunity, List<StudentProfile> candidates) {
        if (candidates.isEmpty()) return Map.of();
        List<Long> studentIds = candidates.stream().map(profile -> profile.getUser().getId()).toList();
        Map<Long, StudentProfessionalProfile> profilesByStudent = new HashMap<>();
        professionalProfiles.findAllByStudentIdIn(studentIds).forEach(profile -> profilesByStudent.put(profile.getStudent().getId(), profile));
        List<Long> profileIds = profilesByStudent.values().stream().map(StudentProfessionalProfile::getId).toList();
        Map<Long, List<StudentSkill>> skillsByProfile = new HashMap<>();
        if (!profileIds.isEmpty()) studentSkills.findAllByProfessionalProfileIdInAndActiveTrue(profileIds)
                .forEach(skill -> skillsByProfile.computeIfAbsent(skill.getProfessionalProfile().getId(), ignored -> new ArrayList<>()).add(skill));
        Map<Long, StudentResume> resumeByProfile = new HashMap<>();
        if (!profileIds.isEmpty()) resumes.findAllByProfessionalProfileIdInAndActiveTrue(profileIds)
                .forEach(resume -> resumeByProfile.put(resume.getProfessionalProfile().getId(), resume));
        List<Long> resumeIds = resumeByProfile.values().stream().map(StudentResume::getId).toList();
        Map<Long, ResumeAnalysis> analysisByResume = new HashMap<>();
        if (!resumeIds.isEmpty()) resumeAnalyses.findAllWithExperiencesByResumeIdIn(resumeIds)
                .forEach(analysis -> analysisByResume.put(analysis.getResume().getId(), analysis));

        List<OpportunitySkill> authoritative = opportunitySkills.findAllByOpportunityIdOrderByRequirementTypeAscDisplayNameAsc(opportunity.getId());
        List<String> roles = new ArrayList<>(); List<String> responsibilities = new ArrayList<>();
        opportunityAnalyses.findFirstByOpportunityIdOrderByCreatedAtDesc(opportunity.getId())
                .filter(a -> a.getStatus() == OpportunityAiAnalysisStatus.ANALYZED)
                .filter(a -> a.getInputHash().equals(OpportunityAiFingerprint.of(opportunity))).ifPresent(a -> a.getSignals().forEach(signal -> {
                    if (signal.getSignalType() == OpportunityAiSignalType.ROLE) roles.add(signal.getDisplayValue());
                    else responsibilities.add(signal.getDisplayValue());
                }));

        List<PreparedCandidate> prepared = candidates.stream().map(institutional -> {
            Long studentId = institutional.getUser().getId();
            StudentProfessionalProfile professional = profilesByStudent.get(studentId);
            List<StudentSkill> skills = professional == null ? List.of()
                    : skillsByProfile.getOrDefault(professional.getId(), List.of());
            List<String> experience = currentExperience(professional, resumeByProfile, analysisByResume);
            StudentOpportunityMatchResponse.Eligibility candidateEligibility = eligibility(opportunity, institutional);
            OpportunityMatchRequest candidateRequest = request(opportunity, professional, skills, experience, authoritative, roles, responsibilities);
            String notice = skills.isEmpty() ? "No confirmed skills are currently available for semantic skill comparison." : null;
            return new PreparedCandidate(studentId, candidateEligibility, candidateRequest, notice);
        }).toList();

        Map<Long, StudentOpportunityMatchResponse> matches = new LinkedHashMap<>();
        try {
            List<OpportunityMatchResult> results = client.matchAll(prepared.stream().map(PreparedCandidate::request).toList());
            if (results.size() != prepared.size()) throw new OpportunityMatchClientException("AI matching batch response size was invalid", null);
            for (int i = 0; i < prepared.size(); i++) {
                PreparedCandidate candidate = prepared.get(i);
                matches.put(candidate.studentId(), available(opportunity.getId(), candidate.eligibility(), candidate.request(), results.get(i), candidate.notice()));
            }
        } catch (OpportunityMatchClientException e) {
            prepared.forEach(candidate -> matches.put(candidate.studentId(), unavailable(opportunity.getId(), candidate.eligibility(), candidate.notice())));
        }
        return Collections.unmodifiableMap(matches);
    }

    @Transactional(readOnly = true)
    public Map<Long, StudentOpportunityMatchResponse> matchForStudentAcrossOpportunities(UserPrincipal principal, List<Opportunity> values) {
        if (values.isEmpty()) return Map.of();
        StudentProfile institutional = studentProfiles.findByUserId(principal.id()).orElse(null);
        StudentProfessionalProfile professional = professionalProfiles.findByStudentId(principal.id()).orElse(null);
        List<StudentSkill> activeSkills = professional == null ? List.of()
                : studentSkills.findAllByProfessionalProfileIdAndActiveTrueOrderByDisplayNameAsc(professional.getId());
        List<String> experience = currentExperience(professional);
        List<Long> opportunityIds = values.stream().map(Opportunity::getId).toList();
        Map<Long, List<OpportunitySkill>> skillsByOpportunity = new HashMap<>();
        opportunitySkills.findAllByOpportunityIdInOrderByRequirementTypeAscDisplayNameAsc(opportunityIds)
                .forEach(skill -> skillsByOpportunity.computeIfAbsent(skill.getParentOpportunityId(), ignored -> new ArrayList<>()).add(skill));
        Map<Long, OpportunityAiAnalysis> latestAnalysis = new HashMap<>();
        opportunityAnalyses.findAllWithSignalsByOpportunityIdIn(opportunityIds)
                .forEach(analysis -> latestAnalysis.putIfAbsent(analysis.getOpportunity().getId(), analysis));

        List<PreparedOpportunity> prepared = values.stream().map(opportunity -> {
            List<String> roles = new ArrayList<>(); List<String> responsibilities = new ArrayList<>();
            OpportunityAiAnalysis analysis = latestAnalysis.get(opportunity.getId());
            if (analysis != null && analysis.getStatus() == OpportunityAiAnalysisStatus.ANALYZED
                    && analysis.getInputHash().equals(OpportunityAiFingerprint.of(opportunity))) {
                analysis.getSignals().forEach(signal -> {
                    if (signal.getSignalType() == OpportunityAiSignalType.ROLE) roles.add(signal.getDisplayValue());
                    else responsibilities.add(signal.getDisplayValue());
                });
            }
            StudentOpportunityMatchResponse.Eligibility candidateEligibility = eligibility(opportunity, institutional);
            OpportunityMatchRequest candidateRequest = request(opportunity, professional, activeSkills, experience,
                    skillsByOpportunity.getOrDefault(opportunity.getId(), List.of()), roles, responsibilities);
            String notice = activeSkills.isEmpty() ? "No confirmed skills are currently available for semantic skill comparison." : null;
            return new PreparedOpportunity(opportunity.getId(), candidateEligibility, candidateRequest, notice);
        }).toList();
        Map<Long, StudentOpportunityMatchResponse> matches = new LinkedHashMap<>();
        try {
            List<OpportunityMatchResult> results = client.matchAll(prepared.stream().map(PreparedOpportunity::request).toList());
            if (results.size() != prepared.size()) throw new OpportunityMatchClientException("AI matching batch response size was invalid", null);
            for (int i = 0; i < prepared.size(); i++) {
                PreparedOpportunity opportunity = prepared.get(i);
                matches.put(opportunity.opportunityId(), available(opportunity.opportunityId(), opportunity.eligibility(), opportunity.request(), results.get(i), opportunity.notice()));
            }
        } catch (OpportunityMatchClientException e) {
            prepared.forEach(opportunity -> matches.put(opportunity.opportunityId(), unavailable(opportunity.opportunityId(), opportunity.eligibility(), opportunity.notice())));
        }
        return Collections.unmodifiableMap(matches);
    }

    private static List<String> currentExperience(StudentProfessionalProfile professional, Map<Long, StudentResume> resumeByProfile,
            Map<Long, ResumeAnalysis> analysisByResume) {
        if (professional == null) return List.of();
        StudentResume resume = resumeByProfile.get(professional.getId());
        if (resume == null || resume.getParsingStatus() != ResumeParsingStatus.PARSED) return List.of();
        ResumeAnalysis analysis = analysisByResume.get(resume.getId());
        if (analysis == null || analysis.getStatus() != ResumeParsingStatus.PARSED) return List.of();
        return analysis.getExperiences().stream().map(exp -> experienceLabel(exp.getTitle(), exp.getCompany()))
                .filter(value -> !value.isBlank()).toList();
    }

    private static StudentOpportunityMatchResponse available(Long opportunityId, StudentOpportunityMatchResponse.Eligibility eligibility,
            OpportunityMatchRequest request, OpportunityMatchResult result, String notice) {
        validateResult(result, request);
        StudentOpportunityMatchResponse.Explanation explanation = explanation(eligibility, result);
        return new StudentOpportunityMatchResponse(opportunityId, eligibility, "AVAILABLE", null, notice,
                new StudentOpportunityMatchResponse.SemanticEvidence(result.modelName(), result.modelVersion(), result.relatedSkillThreshold(),
                        result.requiredSkillMatches(), result.preferredSkillMatches(), result.roleEvidence(), result.experienceEvidence()), explanation);
    }

    private static StudentOpportunityMatchResponse unavailable(Long opportunityId, StudentOpportunityMatchResponse.Eligibility eligibility, String notice) {
        return new StudentOpportunityMatchResponse(opportunityId, eligibility, "UNAVAILABLE",
                "Professional alignment is temporarily unavailable.", notice, null,
                new StudentOpportunityMatchResponse.Explanation(
                        "Eligibility information is available, but professional alignment could not be analyzed right now.", List.of(), List.of(), List.of()));
    }

    private record PreparedCandidate(Long studentId, StudentOpportunityMatchResponse.Eligibility eligibility,
            OpportunityMatchRequest request, String notice) {}
    private record PreparedOpportunity(Long opportunityId, StudentOpportunityMatchResponse.Eligibility eligibility,
            OpportunityMatchRequest request, String notice) {}

    private static StudentOpportunityMatchResponse.Eligibility eligibility(Opportunity opportunity, StudentProfile student) {
        List<StudentOpportunityMatchResponse.Criterion> criteria = new ArrayList<>();
        String branchStatus = opportunity.getEligibleBranches().isEmpty() ? "NOT_APPLICABLE" : student == null || student.getBranch() == null ? "UNKNOWN"
                : opportunity.getEligibleBranches().contains(student.getBranch()) ? "PASS" : "FAIL";
        criteria.add(new StudentOpportunityMatchResponse.Criterion("BRANCH", branchStatus,
                opportunity.getEligibleBranches().isEmpty() ? "Any branch" : opportunity.getEligibleBranches().stream().sorted().map(Enum::name).toList().toString(),
                student == null || student.getBranch() == null ? null : student.getBranch().name(), criterionMessage(branchStatus)));
        String semesterStatus = opportunity.getEligibleSemesters().isEmpty() ? "NOT_APPLICABLE" : student == null ? "UNKNOWN"
                : opportunity.getEligibleSemesters().contains(student.getSemester()) ? "PASS" : "FAIL";
        criteria.add(new StudentOpportunityMatchResponse.Criterion("SEMESTER", semesterStatus,
                opportunity.getEligibleSemesters().isEmpty() ? "Any semester" : opportunity.getEligibleSemesters().stream().sorted().toList().toString(),
                student == null ? null : String.valueOf(student.getSemester()), criterionMessage(semesterStatus)));
        String cgpaStatus = opportunity.getMinimumCgpa() == null ? "NOT_APPLICABLE" : "UNKNOWN";
        criteria.add(new StudentOpportunityMatchResponse.Criterion("CGPA", cgpaStatus,
                opportunity.getMinimumCgpa() == null ? "No minimum CGPA" : "Minimum " + opportunity.getMinimumCgpa(), null,
                opportunity.getMinimumCgpa() == null ? "No CGPA restriction" : "CGPA is not available in the authoritative student profile"));
        String overall = criteria.stream().anyMatch(c -> c.status().equals("FAIL")) ? "INELIGIBLE"
                : criteria.stream().anyMatch(c -> c.status().equals("UNKNOWN")) ? "UNKNOWN" : "ELIGIBLE";
        return new StudentOpportunityMatchResponse.Eligibility(overall, List.copyOf(criteria));
    }
    private static String criterionMessage(String status) { return switch (status) { case "PASS" -> "Requirement met"; case "FAIL" -> "Requirement not met"; case "UNKNOWN" -> "Student data unavailable"; default -> "No restriction"; }; }

    private List<String> currentExperience(StudentProfessionalProfile professional) {
        if (professional == null) return List.of();
        return resumes.findByProfessionalProfileIdAndActiveTrue(professional.getId())
                .filter(r -> r.getParsingStatus() == ResumeParsingStatus.PARSED).flatMap(r -> resumeAnalyses.findByResumeId(r.getId()))
                .filter(a -> a.getStatus() == ResumeParsingStatus.PARSED).map(a -> a.getExperiences().stream().map(exp ->
                        experienceLabel(exp.getTitle(), exp.getCompany())).filter(value -> !value.isBlank()).toList()).orElse(List.of());
    }
    private static String experienceLabel(String title, String company) {
        String cleanTitle = clean(title); String cleanCompany = clean(company);
        return cleanTitle.isBlank() ? cleanCompany : cleanCompany.isBlank() ? cleanTitle : cleanTitle + " at " + cleanCompany;
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static StudentOpportunityMatchResponse.Explanation explanation(StudentOpportunityMatchResponse.Eligibility eligibility,
            OpportunityMatchResult result) {
        List<StudentOpportunityMatchResponse.SkillExplanation> aligned = new ArrayList<>();
        List<StudentOpportunityMatchResponse.SkillExplanation> strengthen = new ArrayList<>();
        List<StudentOpportunityMatchResponse.SkillExplanation> preferred = new ArrayList<>();
        result.requiredSkillMatches().forEach(match -> {
            if ("MISSING".equals(match.matchType())) strengthen.add(new StudentOpportunityMatchResponse.SkillExplanation(
                    match.opportunitySkill(), match.matchType(), null, "Required by this opportunity but not found in your confirmed skills."));
            else aligned.add(skillExplanation(match, "required"));
        });
        result.preferredSkillMatches().forEach(match -> {
            if ("MISSING".equals(match.matchType())) preferred.add(new StudentOpportunityMatchResponse.SkillExplanation(
                    match.opportunitySkill(), match.matchType(), null, "Preferred by this opportunity; it is not a mandatory requirement."));
            else aligned.add(skillExplanation(match, "preferred"));
        });
        boolean hasAlignment = !aligned.isEmpty() || !result.roleEvidence().isEmpty() || !result.experienceEvidence().isEmpty();
        String summary = switch (eligibility.overall()) {
            case "INELIGIBLE" -> "One or more listed eligibility requirements are not met. Professional alignment is shown separately below.";
            case "UNKNOWN" -> !hasAlignment
                    ? "All eligibility requirements could not be verified. Review your professional profile to improve alignment evidence."
                    : "Your professional profile has relevant alignment, but all eligibility requirements could not be verified.";
            default -> !hasAlignment
                    ? "Known eligibility requirements are satisfied. Review the professional evidence below before making your decision."
                    : strengthen.isEmpty()
                            ? "Your profile aligns with the listed required skills. Review the evidence below before making your decision."
                            : "Your profile aligns with several of this opportunity's requirements. Review the missing skills below before making your decision.";
        };
        return new StudentOpportunityMatchResponse.Explanation(summary, List.copyOf(aligned), List.copyOf(strengthen), List.copyOf(preferred));
    }
    private static StudentOpportunityMatchResponse.SkillExplanation skillExplanation(OpportunityMatchResult.SkillMatch match, String kind) {
        String detail = "EXACT".equals(match.matchType())
                ? ("preferred".equals(kind) ? "Exact match for a preferred skill." : "Exact required skill match.")
                : "Related " + kind + " skill: " + match.studentSkill() + ".";
        return new StudentOpportunityMatchResponse.SkillExplanation(match.opportunitySkill(), match.matchType(), match.studentSkill(), detail);
    }
    private static OpportunityMatchRequest request(Opportunity opportunity, StudentProfessionalProfile professional, List<StudentSkill> studentSkills,
            List<String> experience, List<OpportunitySkill> opportunitySkills, List<String> roles, List<String> responsibilities) {
        List<OpportunityMatchRequest.Skill> own = studentSkills.stream().map(s -> new OpportunityMatchRequest.Skill(s.getDisplayName(), s.getNormalizedName())).toList();
        List<OpportunityMatchRequest.Skill> required = opportunitySkills.stream().filter(s -> s.getRequirementType() == OpportunitySkillType.REQUIRED)
                .map(s -> new OpportunityMatchRequest.Skill(s.getDisplayName(), s.getNormalizedName())).toList();
        List<OpportunityMatchRequest.Skill> preferred = opportunitySkills.stream().filter(s -> s.getRequirementType() == OpportunitySkillType.PREFERRED)
                .map(s -> new OpportunityMatchRequest.Skill(s.getDisplayName(), s.getNormalizedName())).toList();
        String headline = professional == null || professional.getHeadline() == null ? "" : professional.getHeadline();
        List<String> interests = professional == null || professional.getCareerInterests() == null ? List.of() : Arrays.stream(professional.getCareerInterests().split("[,;\\n]"))
                .map(String::trim).filter(v -> !v.isBlank()).limit(20).toList();
        return new OpportunityMatchRequest(new OpportunityMatchRequest.Student(own, headline, interests, experience),
                new OpportunityMatchRequest.OpportunityData(opportunity.getTitle(), clean(opportunity.getDescription()), required, preferred, roles, responsibilities));
    }
    private static void validateResult(OpportunityMatchResult result, OpportunityMatchRequest request) {
        validateSkillResults(result.requiredSkillMatches(), request.opportunity().requiredSkills(), request.student().skills());
        validateSkillResults(result.preferredSkillMatches(), request.opportunity().preferredSkills(), request.student().skills());
    }
    private static void validateSkillResults(List<OpportunityMatchResult.SkillMatch> results, List<OpportunityMatchRequest.Skill> expected,
            List<OpportunityMatchRequest.Skill> studentSkills) {
        if (results.size() != expected.size()) throw new OpportunityMatchClientException("AI matching response did not cover authoritative skills", null);
        for (int i = 0; i < expected.size(); i++) {
            OpportunityMatchResult.SkillMatch result = results.get(i); OpportunityMatchRequest.Skill opportunity = expected.get(i);
            if (!result.opportunitySkill().equals(opportunity.name()))
                throw new OpportunityMatchClientException("AI matching response did not match authoritative skills", null);
            if (!"MISSING".equals(result.matchType())) {
                OpportunityMatchRequest.Skill student = studentSkills.stream().filter(value -> value.name().equals(result.studentSkill())).findFirst()
                        .orElseThrow(() -> new OpportunityMatchClientException("AI matching response referenced an unknown student skill", null));
                if ("EXACT".equals(result.matchType()) && !student.normalizedName().equals(opportunity.normalizedName()))
                    throw new OpportunityMatchClientException("AI matching response returned a non-exact skill as exact", null);
            }
        }
    }
}
