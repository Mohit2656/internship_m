package edu.institution.ims.professionalprofile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.opportunity.OpportunitySkillRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
public class SkillNormalizer {
    private static final Pattern ALLOWED = Pattern.compile("^[\\p{L}\\p{N} .+#/&()_-]+$");
    private static final Pattern HAS_TEXT = Pattern.compile(".*[\\p{L}\\p{N}].*");

    private static final CatalogData CATALOG = loadCatalog();
    private static final Set<String> STOPWORDS = CATALOG.stopwords();
    private static final Set<String> INVALID_CANDIDATES = CATALOG.invalidCandidates();
    private static final Map<String, String> CANONICAL = CATALOG.canonical();

    private record CatalogData(Set<String> stopwords, Set<String> invalidCandidates, Map<String, String> canonical) {}

    private static CatalogData loadCatalog() {
        try (InputStream stream = SkillNormalizer.class.getResourceAsStream("/skills-catalog.json")) {
            if (stream == null) {
                throw new IllegalStateException("Missing /skills-catalog.json on classpath");
            }
            JsonNode root = new ObjectMapper().readTree(stream);
            Set<String> stopwords = readLowerSet(root.get("stopwords"));
            Set<String> invalid = readLowerSet(root.get("invalidCandidates"));
            Map<String, String> canonical = new HashMap<>();
            JsonNode skills = root.get("skills");
            if (skills != null && skills.isArray()) {
                for (JsonNode entry : skills) {
                    String displayName = entry.path("displayName").asText("").trim();
                    if (displayName.isEmpty()) continue;
                    canonical.put(displayName.toLowerCase(Locale.ROOT), displayName);
                    JsonNode aliases = entry.get("aliases");
                    if (aliases != null && aliases.isArray()) {
                        for (JsonNode aliasNode : aliases) {
                            String alias = aliasNode.asText("").trim().toLowerCase(Locale.ROOT);
                            if (!alias.isEmpty()) {
                                canonical.put(alias, displayName);
                            }
                        }
                    }
                }
            }
            return new CatalogData(
                    Collections.unmodifiableSet(stopwords),
                    Collections.unmodifiableSet(invalid),
                    Collections.unmodifiableMap(canonical));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load /skills-catalog.json", e);
        }
    }

    private static Set<String> readLowerSet(JsonNode node) {
        Set<String> result = new HashSet<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText("").trim().toLowerCase(Locale.ROOT);
                if (!value.isEmpty()) result.add(value);
            }
        }
        return result;
    }

    @Autowired(required = false)
    @Lazy
    private OpportunitySkillRepository opportunitySkills;

    private final Map<String, String> dynamicSkills = new ConcurrentHashMap<>();

    public SkillNormalizer() {}

    public SkillNormalizer(OpportunitySkillRepository opportunitySkills) {
        this.opportunitySkills = opportunitySkills;
    }

    public void setOpportunitySkillRepository(OpportunitySkillRepository opportunitySkills) {
        this.opportunitySkills = opportunitySkills;
    }

    public void registerSkill(String displayName, String... aliases) {
        if (displayName == null || displayName.isBlank()) return;
        String cleanDisplay = Normalizer.normalize(displayName, Normalizer.Form.NFKC).trim().replaceAll("\\s+", " ");
        String key = cleanDisplay.toLowerCase(Locale.ROOT);
        dynamicSkills.put(key, cleanDisplay);
        if (aliases != null) {
            for (String alias : aliases) {
                if (alias != null && !alias.isBlank()) {
                    String cleanAlias = Normalizer.normalize(alias, Normalizer.Form.NFKC).trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
                    dynamicSkills.put(cleanAlias, cleanDisplay);
                }
            }
        }
    }

    public NormalizedSkill normalize(String value) {
        if (value == null) throw invalid();
        String clean = Normalizer.normalize(value, Normalizer.Form.NFKC).trim().replaceAll("\\s+", " ");
        if (clean.isEmpty() || clean.length() > 100 || !ALLOWED.matcher(clean).matches() || !HAS_TEXT.matcher(clean).matches()) throw invalid();
        String key = clean.toLowerCase(Locale.ROOT);
        if (STOPWORDS.contains(key) || INVALID_CANDIDATES.contains(key)) throw invalid();

        String display = CANONICAL.get(key);
        if (display != null) {
            return new NormalizedSkill(display, display.toLowerCase(Locale.ROOT));
        }

        String dyn = dynamicSkills.get(key);
        if (dyn != null) {
            return new NormalizedSkill(dyn, dyn.toLowerCase(Locale.ROOT));
        }

        if (opportunitySkills != null) {
            try {
                var matches = opportunitySkills.findDisplayNamesByNormalizedName(key);
                if (matches != null && !matches.isEmpty()) {
                    String matchedDisplay = matches.get(0);
                    dynamicSkills.put(key, matchedDisplay);
                    return new NormalizedSkill(matchedDisplay, matchedDisplay.toLowerCase(Locale.ROOT));
                }
            } catch (Exception ignored) {}
        }

        return new NormalizedSkill(clean, key);
    }

    /** Catalog lookup for machine-extracted candidates. */
    public Optional<NormalizedSkill> recognize(String value) {
        if (value == null) return Optional.empty();
        String clean = Normalizer.normalize(value, Normalizer.Form.NFKC).trim().replaceAll("\\s+", " ");
        if (clean.isEmpty() || clean.length() > 100 || !ALLOWED.matcher(clean).matches() || !HAS_TEXT.matcher(clean).matches()) return Optional.empty();
        String key = clean.toLowerCase(Locale.ROOT);
        if (STOPWORDS.contains(key) || INVALID_CANDIDATES.contains(key)) return Optional.empty();

        String display = CANONICAL.get(key);
        if (display != null) {
            return Optional.of(new NormalizedSkill(display, display.toLowerCase(Locale.ROOT)));
        }

        String dyn = dynamicSkills.get(key);
        if (dyn != null) {
            return Optional.of(new NormalizedSkill(dyn, dyn.toLowerCase(Locale.ROOT)));
        }

        if (opportunitySkills != null) {
            try {
                var matches = opportunitySkills.findDisplayNamesByNormalizedName(key);
                if (matches != null && !matches.isEmpty()) {
                    String matchedDisplay = matches.get(0);
                    dynamicSkills.put(key, matchedDisplay);
                    return Optional.of(new NormalizedSkill(matchedDisplay, matchedDisplay.toLowerCase(Locale.ROOT)));
                }
            } catch (Exception ignored) {}
        }

        return Optional.empty();
    }

    private static ResumeException invalid() {
        return new ResumeException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_SKILL", "Enter a valid skill name up to 100 characters");
    }
}
