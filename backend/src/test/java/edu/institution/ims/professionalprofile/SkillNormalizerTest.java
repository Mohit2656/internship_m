package edu.institution.ims.professionalprofile;

import edu.institution.ims.opportunity.OpportunitySkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SkillNormalizerTest {

    private SkillNormalizer normalizer;
    private OpportunitySkillRepository opportunitySkills;

    @BeforeEach
    void setUp() {
        opportunitySkills = mock(OpportunitySkillRepository.class);
        normalizer = new SkillNormalizer(opportunitySkills);
    }

    @Test
    void recognizesCanonicalSkillsAcrossAllCategories() {
        // Programming
        assertThat(normalizer.recognize("Java")).map(NormalizedSkill::displayName).contains("Java");
        assertThat(normalizer.recognize("Python")).map(NormalizedSkill::displayName).contains("Python");
        assertThat(normalizer.recognize("Rust")).map(NormalizedSkill::displayName).contains("Rust");
        assertThat(normalizer.recognize("Kotlin")).map(NormalizedSkill::displayName).contains("Kotlin");
        assertThat(normalizer.recognize("SQL")).map(NormalizedSkill::displayName).contains("SQL");

        // Frontend
        assertThat(normalizer.recognize("React")).map(NormalizedSkill::displayName).contains("React");
        assertThat(normalizer.recognize("Angular")).map(NormalizedSkill::displayName).contains("Angular");
        assertThat(normalizer.recognize("Vue")).map(NormalizedSkill::displayName).contains("Vue");
        assertThat(normalizer.recognize("Next.js")).map(NormalizedSkill::displayName).contains("Next.js");
        assertThat(normalizer.recognize("Tailwind CSS")).map(NormalizedSkill::displayName).contains("Tailwind CSS");

        // Backend
        assertThat(normalizer.recognize("Spring Boot")).map(NormalizedSkill::displayName).contains("Spring Boot");
        assertThat(normalizer.recognize("Express")).map(NormalizedSkill::displayName).contains("Express");
        assertThat(normalizer.recognize("Django")).map(NormalizedSkill::displayName).contains("Django");
        assertThat(normalizer.recognize("FastAPI")).map(NormalizedSkill::displayName).contains("FastAPI");

        // Databases
        assertThat(normalizer.recognize("PostgreSQL")).map(NormalizedSkill::displayName).contains("PostgreSQL");
        assertThat(normalizer.recognize("MongoDB")).map(NormalizedSkill::displayName).contains("MongoDB");
        assertThat(normalizer.recognize("Redis")).map(NormalizedSkill::displayName).contains("Redis");

        // Cloud & DevOps
        assertThat(normalizer.recognize("Docker")).map(NormalizedSkill::displayName).contains("Docker");
        assertThat(normalizer.recognize("Kubernetes")).map(NormalizedSkill::displayName).contains("Kubernetes");
        assertThat(normalizer.recognize("AWS")).map(NormalizedSkill::displayName).contains("AWS");
        assertThat(normalizer.recognize("CI/CD")).map(NormalizedSkill::displayName).contains("CI/CD");

        // AI / ML / Data
        assertThat(normalizer.recognize("Machine Learning")).map(NormalizedSkill::displayName).contains("Machine Learning");
        assertThat(normalizer.recognize("Deep Learning")).map(NormalizedSkill::displayName).contains("Deep Learning");
        assertThat(normalizer.recognize("Data Science")).map(NormalizedSkill::displayName).contains("Data Science");
        assertThat(normalizer.recognize("Power BI")).map(NormalizedSkill::displayName).contains("Power BI");

        // Testing & Professional
        assertThat(normalizer.recognize("JUnit")).map(NormalizedSkill::displayName).contains("JUnit");
        assertThat(normalizer.recognize("Selenium")).map(NormalizedSkill::displayName).contains("Selenium");
        assertThat(normalizer.recognize("REST APIs")).map(NormalizedSkill::displayName).contains("REST APIs");
        assertThat(normalizer.recognize("Microservices")).map(NormalizedSkill::displayName).contains("Microservices");
        assertThat(normalizer.recognize("Cybersecurity")).map(NormalizedSkill::displayName).contains("Cybersecurity");
    }

    @Test
    void normalizesAliasesToCanonicalEquivalent() {
        // React aliases
        assertThat(normalizer.normalize("ReactJS")).isEqualTo(new NormalizedSkill("React", "react"));
        assertThat(normalizer.normalize("react.js")).isEqualTo(new NormalizedSkill("React", "react"));
        assertThat(normalizer.recognize("react-js")).map(NormalizedSkill::displayName).contains("React");

        // Postgres
        assertThat(normalizer.normalize("postgres")).isEqualTo(new NormalizedSkill("PostgreSQL", "postgresql"));
        assertThat(normalizer.recognize("Postgres")).map(NormalizedSkill::displayName).contains("PostgreSQL");

        // Kubernetes
        assertThat(normalizer.normalize("k8s")).isEqualTo(new NormalizedSkill("Kubernetes", "kubernetes"));
        assertThat(normalizer.recognize("k8s")).map(NormalizedSkill::displayName).contains("Kubernetes");

        // Languages & frameworks
        assertThat(normalizer.normalize("golang")).isEqualTo(new NormalizedSkill("Go", "go"));
        assertThat(normalizer.normalize("cpp")).isEqualTo(new NormalizedSkill("C++", "c++"));
        assertThat(normalizer.normalize("csharp")).isEqualTo(new NormalizedSkill("C#", "c#"));
        assertThat(normalizer.normalize("js")).isEqualTo(new NormalizedSkill("JavaScript", "javascript"));
        assertThat(normalizer.normalize("ts")).isEqualTo(new NormalizedSkill("TypeScript", "typescript"));
        assertThat(normalizer.normalize("nodejs")).isEqualTo(new NormalizedSkill("Node.js", "node.js"));
        assertThat(normalizer.normalize("spring")).isEqualTo(new NormalizedSkill("Spring Boot", "spring boot"));
        assertThat(normalizer.normalize("ml")).isEqualTo(new NormalizedSkill("Machine Learning", "machine learning"));
        assertThat(normalizer.normalize("ai")).isEqualTo(new NormalizedSkill("Artificial Intelligence", "artificial intelligence"));
        assertThat(normalizer.normalize("oop")).isEqualTo(new NormalizedSkill("Object-Oriented Programming", "object-oriented programming"));
    }

    @Test
    void rejectsStopwordsAndOrdinaryWords() {
        for (String word : List.of("is", "the", "are", "and", "or", "in", "to", "for", "with", "a", "an", "of", "this", "that")) {
            assertThat(normalizer.recognize(word)).isEmpty();
            assertThatThrownBy(() -> normalizer.normalize(word))
                    .isInstanceOf(ResumeException.class)
                    .hasMessageContaining("Enter a valid skill name");
        }
    }

    @Test
    void rejectsNarrativeInvalidCandidates() {
        for (String invalid : List.of("experience", "work", "developer", "internship", "project", "technologies")) {
            assertThat(normalizer.recognize(invalid)).isEmpty();
            assertThatThrownBy(() -> normalizer.normalize(invalid))
                    .isInstanceOf(ResumeException.class)
                    .hasMessageContaining("Enter a valid skill name");
        }
    }

    @Test
    void doesNotPerformArbitrarySubstringMatching() {
        // Words containing stopwords as substrings should not be matched to stopwords,
        // and words containing skills as substrings should not match the skill unless recognized
        assertThat(normalizer.recognize("JavaScripted")).isEmpty();
        assertThat(normalizer.recognize("island")).isEmpty();
        assertThat(normalizer.recognize("theme")).isEmpty();
    }

    @Test
    void preservesShortSkills() {
        assertThat(normalizer.recognize("C")).map(NormalizedSkill::displayName).contains("C");
        assertThat(normalizer.recognize("R")).map(NormalizedSkill::displayName).contains("R");
        assertThat(normalizer.recognize("Go")).map(NormalizedSkill::displayName).contains("Go");
        assertThat(normalizer.recognize("C++")).map(NormalizedSkill::displayName).contains("C++");
        assertThat(normalizer.recognize("C#")).map(NormalizedSkill::displayName).contains("C#");
    }

    @Test
    void allowsAdministratorsToAddSkillsDynamicallyWithoutCodeChanges() {
        // 1. Through OpportunitySkillRepository (existing admin Opportunity CRUD)
        when(opportunitySkills.findDisplayNamesByNormalizedName("solidity")).thenReturn(List.of("Solidity"));

        Optional<NormalizedSkill> recognized = normalizer.recognize("Solidity");
        assertThat(recognized).isPresent();
        assertThat(recognized.get().displayName()).isEqualTo("Solidity");
        assertThat(recognized.get().normalizedName()).isEqualTo("solidity");

        NormalizedSkill normalized = normalizer.normalize("Solidity");
        assertThat(normalized.displayName()).isEqualTo("Solidity");
        assertThat(normalized.normalizedName()).isEqualTo("solidity");

        // 2. Through runtime registration
        normalizer.registerSkill("WebAssembly", "wasm");
        assertThat(normalizer.recognize("wasm")).map(NormalizedSkill::displayName).contains("WebAssembly");
        assertThat(normalizer.normalize("wasm").displayName()).isEqualTo("WebAssembly");
    }
}
