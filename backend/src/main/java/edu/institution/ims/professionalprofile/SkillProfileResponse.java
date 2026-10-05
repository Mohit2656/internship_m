package edu.institution.ims.professionalprofile;

import java.util.List;

public record SkillProfileResponse(List<SkillResponse> skills, List<SuggestionResponse> suggestions) {
    public record SkillResponse(Long id, String displayName, StudentSkillSource source) {
        static SkillResponse from(StudentSkill skill) { return new SkillResponse(skill.getId(), skill.getDisplayName(), skill.getSource()); }
    }
    public record SuggestionResponse(Long id, String displayName) {
        static SuggestionResponse from(StudentSkillSuggestion suggestion) { return new SuggestionResponse(suggestion.getId(), suggestion.getDisplayName()); }
    }
    static SkillProfileResponse empty() { return new SkillProfileResponse(List.of(), List.of()); }
}
