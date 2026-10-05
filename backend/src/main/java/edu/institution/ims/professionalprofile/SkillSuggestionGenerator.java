package edu.institution.ims.professionalprofile;

import org.springframework.stereotype.Component;

@Component
public class SkillSuggestionGenerator {
    private final StudentSkillRepository skills;
    private final StudentSkillSuggestionRepository suggestions;
    private final SkillNormalizer normalizer;

    public SkillSuggestionGenerator(StudentSkillRepository skills, StudentSkillSuggestionRepository suggestions, SkillNormalizer normalizer) {
        this.skills = skills; this.suggestions = suggestions; this.normalizer = normalizer;
    }

    public void generate(ResumeAnalysis analysis) {
        StudentProfessionalProfile profile = analysis.getResume().getProfessionalProfile();
        for (ResumeExtractedSkill extracted : analysis.getSkills()) {
            if (suggestions.existsByExtractedSkillId(extracted.getId())) continue;
            NormalizedSkill value;
            try { value = normalizer.normalize(extracted.getName()); }
            catch (ResumeException ignored) { continue; }
            StudentSkill existing = skills.findByProfessionalProfileIdAndNormalizedNameAndActiveTrue(profile.getId(), value.normalizedName()).orElse(null);
            suggestions.save(new StudentSkillSuggestion(profile, analysis, extracted, value, existing));
        }
        suggestions.flush();
    }
}
