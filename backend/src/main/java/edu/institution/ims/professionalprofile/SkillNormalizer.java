package edu.institution.ims.professionalprofile;

import org.springframework.stereotype.Component;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class SkillNormalizer {
    private static final Pattern ALLOWED = Pattern.compile("^[\\p{L}\\p{N} .+#/&()_-]+$");
    private static final Pattern HAS_TEXT = Pattern.compile(".*[\\p{L}\\p{N}].*");
    private static final Map<String, String> CANONICAL = Map.ofEntries(
            Map.entry("java", "Java"), Map.entry("spring boot", "Spring Boot"), Map.entry("postgresql", "PostgreSQL"),
            Map.entry("mysql", "MySQL"), Map.entry("javascript", "JavaScript"), Map.entry("typescript", "TypeScript"),
            Map.entry("git hub", "GitHub"), Map.entry("github", "GitHub"), Map.entry("node.js", "Node.js"),
            Map.entry("html", "HTML"), Map.entry("css", "CSS"), Map.entry("html/css", "HTML/CSS"),
            Map.entry("docker", "Docker"), Map.entry("kubernetes", "Kubernetes"), Map.entry("aws", "AWS"),
            Map.entry("c++", "C++"), Map.entry("c#", "C#"));

    public NormalizedSkill normalize(String value) {
        if (value == null) throw invalid();
        String clean = Normalizer.normalize(value, Normalizer.Form.NFKC).trim().replaceAll("\\s+", " ");
        if (clean.isEmpty() || clean.length() > 100 || !ALLOWED.matcher(clean).matches() || !HAS_TEXT.matcher(clean).matches()) throw invalid();
        String key = clean.toLowerCase(Locale.ROOT);
        String display = CANONICAL.getOrDefault(key, clean);
        String normalized = CANONICAL.containsKey(key) ? display.toLowerCase(Locale.ROOT) : key;
        return new NormalizedSkill(display, normalized);
    }

    private static ResumeException invalid() {
        return new ResumeException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_SKILL", "Enter a valid skill name up to 100 characters");
    }
}
