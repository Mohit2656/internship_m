package edu.institution.ims.professionalprofile;

import org.springframework.stereotype.Component;
import java.util.List;

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
        List<StudentSkill> previousResumeSkills = skills.findAllByProfessionalProfileIdAndSourceAndActiveTrue(
                profile.getId(), StudentSkillSource.AI_EXTRACTED);
        previousResumeSkills.stream()
                .filter(skill -> skill.getSourceResumeAnalysis() == null
                        || !skill.getSourceResumeAnalysis().getId().equals(analysis.getId()))
                .forEach(StudentSkill::deactivate);
        skills.saveAll(previousResumeSkills);
        skills.flush();

        for (ResumeExtractedSkill extracted : analysis.getSkills()) {
            NormalizedSkill value = normalizer.recognize(extracted.getName()).orElse(null);
            if (value == null) continue;
            StudentSkill existing = skills.findByProfessionalProfileIdAndNormalizedNameAndActiveTrue(profile.getId(), value.normalizedName()).orElse(null);
            if (existing == null) skills.save(new StudentSkill(profile, value, StudentSkillSource.AI_EXTRACTED, analysis));
        }
        skills.flush();
    }
}
