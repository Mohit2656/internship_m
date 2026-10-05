package edu.institution.ims.professionalprofile;

import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class StudentSkillService {
    private final StudentProfessionalProfileRepository profiles;
    private final StudentResumeRepository resumes;
    private final ResumeAnalysisRepository analyses;
    private final StudentSkillRepository skills;
    private final StudentSkillSuggestionRepository suggestions;
    private final UserRepository users;
    private final SkillNormalizer normalizer;

    public StudentSkillService(StudentProfessionalProfileRepository profiles, StudentResumeRepository resumes,
            ResumeAnalysisRepository analyses, StudentSkillRepository skills, StudentSkillSuggestionRepository suggestions,
            UserRepository users, SkillNormalizer normalizer) {
        this.profiles = profiles; this.resumes = resumes; this.analyses = analyses; this.skills = skills;
        this.suggestions = suggestions; this.users = users; this.normalizer = normalizer;
    }

    @Transactional(readOnly = true)
    public SkillProfileResponse getOwn(UserPrincipal principal) {
        return profiles.findByStudentId(principal.id()).map(this::response).orElseGet(SkillProfileResponse::empty);
    }

    @Transactional(readOnly = true)
    public List<SkillProfileResponse.SkillResponse> confirmedForStudent(Long studentId) {
        requireStudent(studentId);
        return profiles.findByStudentId(studentId)
                .map(profile -> skills.findAllByProfessionalProfileIdAndActiveTrueOrderByDisplayNameAsc(profile.getId())
                        .stream().map(SkillProfileResponse.SkillResponse::from).toList())
                .orElseGet(List::of);
    }

    @Transactional
    public SkillProfileResponse addOwn(UserPrincipal principal, SkillNameRequest request) {
        StudentProfessionalProfile profile = profileForUpdateOrCreate(principal.id());
        NormalizedSkill value = normalizer.normalize(request.name());
        ensureUnique(profile.getId(), value.normalizedName(), null);
        skills.saveAndFlush(new StudentSkill(profile, value, StudentSkillSource.STUDENT_ADDED, null));
        return response(profile);
    }

    @Transactional
    public SkillProfileResponse renameOwn(UserPrincipal principal, Long skillId, SkillNameRequest request) {
        StudentSkill skill = ownActiveSkill(principal.id(), skillId);
        NormalizedSkill value = normalizer.normalize(request.name());
        ensureUnique(skill.getProfessionalProfile().getId(), value.normalizedName(), skill.getId());
        skill.rename(value);
        skills.saveAndFlush(skill);
        return response(skill.getProfessionalProfile());
    }

    @Transactional
    public SkillProfileResponse removeOwn(UserPrincipal principal, Long skillId) {
        StudentSkill skill = ownActiveSkill(principal.id(), skillId);
        skill.deactivate();
        skills.saveAndFlush(skill);
        return response(skill.getProfessionalProfile());
    }

    @Transactional
    public SkillProfileResponse confirmOwn(UserPrincipal principal, Long suggestionId) {
        StudentSkillSuggestion suggestion = ownCurrentSuggestion(principal.id(), suggestionId);
        if (suggestion.getReviewStatus() == SkillReviewStatus.REJECTED)
            throw error(HttpStatus.CONFLICT, "SUGGESTION_ALREADY_REJECTED", "This suggestion has already been removed");
        if (suggestion.getReviewStatus() == SkillReviewStatus.PENDING) confirm(suggestion);
        return response(suggestion.getProfessionalProfile());
    }

    @Transactional
    public SkillProfileResponse rejectOwn(UserPrincipal principal, Long suggestionId) {
        StudentSkillSuggestion suggestion = ownCurrentSuggestion(principal.id(), suggestionId);
        if (suggestion.getReviewStatus() == SkillReviewStatus.CONFIRMED)
            throw error(HttpStatus.CONFLICT, "SUGGESTION_ALREADY_CONFIRMED", "This suggestion has already been confirmed");
        if (suggestion.getReviewStatus() == SkillReviewStatus.PENDING) {
            suggestion.reject();
            suggestions.saveAndFlush(suggestion);
        }
        return response(suggestion.getProfessionalProfile());
    }

    @Transactional
    public SkillProfileResponse confirmAllOwn(UserPrincipal principal) {
        StudentProfessionalProfile profile = profiles.findByStudentIdForUpdate(principal.id())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "PROFESSIONAL_PROFILE_NOT_FOUND", "Professional profile was not found"));
        ResumeAnalysis current = currentParsedAnalysis(profile).orElse(null);
        if (current != null) {
            List<StudentSkillSuggestion> pending = suggestions.findAllByResumeAnalysisIdAndReviewStatusOrderByDisplayNameAsc(
                    current.getId(), SkillReviewStatus.PENDING);
            pending.forEach(this::confirm);
            suggestions.saveAll(pending);
            suggestions.flush();
        }
        return response(profile);
    }

    private void confirm(StudentSkillSuggestion suggestion) {
        StudentProfessionalProfile profile = suggestion.getProfessionalProfile();
        StudentSkill skill = skills.findByProfessionalProfileIdAndNormalizedNameAndActiveTrue(profile.getId(), suggestion.getNormalizedName())
                .orElseGet(() -> skills.saveAndFlush(new StudentSkill(profile,
                        new NormalizedSkill(suggestion.getDisplayName(), suggestion.getNormalizedName()),
                        StudentSkillSource.AI_EXTRACTED, suggestion.getResumeAnalysis())));
        suggestion.confirm(skill);
    }

    private SkillProfileResponse response(StudentProfessionalProfile profile) {
        List<SkillProfileResponse.SkillResponse> active = skills.findAllByProfessionalProfileIdAndActiveTrueOrderByDisplayNameAsc(profile.getId())
                .stream().map(SkillProfileResponse.SkillResponse::from).toList();
        List<SkillProfileResponse.SuggestionResponse> pending = currentParsedAnalysis(profile)
                .map(analysis -> suggestions.findAllByResumeAnalysisIdAndReviewStatusOrderByDisplayNameAsc(analysis.getId(), SkillReviewStatus.PENDING)
                        .stream().map(SkillProfileResponse.SuggestionResponse::from).toList())
                .orElseGet(List::of);
        return new SkillProfileResponse(active, pending);
    }

    private Optional<ResumeAnalysis> currentParsedAnalysis(StudentProfessionalProfile profile) {
        return resumes.findByProfessionalProfileIdAndActiveTrue(profile.getId())
                .flatMap(resume -> analyses.findByResumeId(resume.getId()))
                .filter(analysis -> analysis.getStatus() == ResumeParsingStatus.PARSED);
    }

    private StudentSkillSuggestion ownCurrentSuggestion(Long studentId, Long suggestionId) {
        StudentSkillSuggestion suggestion = suggestions.findByIdAndProfessionalProfileStudentId(suggestionId, studentId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "SKILL_SUGGESTION_NOT_FOUND", "Skill suggestion was not found"));
        if (!suggestion.getResumeAnalysis().getResume().isActive())
            throw error(HttpStatus.CONFLICT, "STALE_SKILL_SUGGESTION", "This suggestion belongs to an older resume");
        return suggestion;
    }

    private StudentSkill ownActiveSkill(Long studentId, Long skillId) {
        return skills.findByIdAndProfessionalProfileStudentIdAndActiveTrue(skillId, studentId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "SKILL_NOT_FOUND", "Skill was not found"));
    }

    private void ensureUnique(Long profileId, String normalized, Long allowedId) {
        skills.findByProfessionalProfileIdAndNormalizedNameAndActiveTrue(profileId, normalized).ifPresent(existing -> {
            if (!existing.getId().equals(allowedId)) throw error(HttpStatus.CONFLICT, "SKILL_ALREADY_EXISTS", "This skill is already in your profile");
        });
    }

    private StudentProfessionalProfile profileForUpdateOrCreate(Long studentId) {
        return profiles.findByStudentIdForUpdate(studentId).orElseGet(() ->
                profiles.saveAndFlush(new StudentProfessionalProfile(requireStudent(studentId))));
    }

    private User requireStudent(Long studentId) {
        return users.findById(studentId).filter(user -> user.getRole() == Role.STUDENT)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "Student account was not found"));
    }

    private static ResumeException error(HttpStatus status, String code, String message) { return new ResumeException(status, code, message); }
}
