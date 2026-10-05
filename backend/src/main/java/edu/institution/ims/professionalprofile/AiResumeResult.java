package edu.institution.ims.professionalprofile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiResumeResult(String modelName, String modelVersion, List<String> skills,
        List<AiExperience> experience, List<AiEducation> education, List<String> certifications) {

    public AiResumeResult validated() {
        String name = required(modelName, 200, "modelName");
        String version = required(modelVersion, 200, "modelVersion");
        List<String> cleanSkills = strings(skills, 200, 200, "skills");
        List<String> cleanCertifications = strings(certifications, 100, 400, "certifications");
        if (experience == null || experience.size() > 100 || education == null || education.size() > 100)
            throw new IllegalArgumentException("AI response contains an invalid collection");
        List<AiExperience> cleanExperience = experience.stream().map(item -> {
            if (item == null) throw new IllegalArgumentException("AI response contains an invalid experience");
            AiExperience value = new AiExperience(optional(item.title(), 300), optional(item.company(), 300),
                    optional(item.startDate(), 100), optional(item.endDate(), 100));
            if (value.title() == null && value.company() == null) throw new IllegalArgumentException("AI response contains an empty experience");
            return value;
        }).toList();
        List<AiEducation> cleanEducation = education.stream().map(item -> {
            if (item == null) throw new IllegalArgumentException("AI response contains an invalid education entry");
            AiEducation value = new AiEducation(optional(item.degree(), 300), optional(item.field(), 300), optional(item.institution(), 400));
            if (value.degree() == null && value.field() == null && value.institution() == null)
                throw new IllegalArgumentException("AI response contains an empty education entry");
            return value;
        }).toList();
        return new AiResumeResult(name, version, cleanSkills, cleanExperience, cleanEducation, cleanCertifications);
    }

    private static List<String> strings(List<String> values, int maxItems, int maxLength, String field) {
        if (values == null || values.size() > maxItems) throw new IllegalArgumentException("AI response contains invalid " + field);
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        for (String value : values) {
            String clean = required(value, maxLength, field);
            unique.putIfAbsent(clean.toLowerCase(Locale.ROOT), clean);
        }
        return List.copyOf(unique.values());
    }

    private static String required(String value, int maxLength, String field) {
        String clean = optional(value, maxLength);
        if (clean == null) throw new IllegalArgumentException("AI response is missing " + field);
        return clean;
    }

    private static String optional(String value, int maxLength) {
        if (value == null) return null;
        String clean = value.trim().replaceAll("\\s+", " ");
        if (clean.isEmpty()) return null;
        if (clean.length() > maxLength) throw new IllegalArgumentException("AI response value is too long");
        return clean;
    }
}
