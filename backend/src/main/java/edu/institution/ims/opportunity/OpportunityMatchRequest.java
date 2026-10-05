package edu.institution.ims.opportunity;

import java.util.List;

public record OpportunityMatchRequest(Student student, OpportunityData opportunity) {
    public record Skill(String name, String normalizedName) {}
    public record Student(List<Skill> skills, String headline, List<String> careerInterests, List<String> experience) {}
    public record OpportunityData(String title, String description, List<Skill> requiredSkills, List<Skill> preferredSkills,
            List<String> roleSignals, List<String> responsibilitySignals) {}
}
