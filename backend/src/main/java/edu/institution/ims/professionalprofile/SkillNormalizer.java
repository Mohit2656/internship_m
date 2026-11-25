package edu.institution.ims.professionalprofile;

import edu.institution.ims.opportunity.OpportunitySkillRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
public class SkillNormalizer {
    private static final Pattern ALLOWED = Pattern.compile("^[\\p{L}\\p{N} .+#/&()_-]+$");
    private static final Pattern HAS_TEXT = Pattern.compile(".*[\\p{L}\\p{N}].*");

    private static final Set<String> STOPWORDS = Set.of(
            "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "aren't",
            "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but", "by",
            "can", "can't", "cannot", "could", "couldn't", "did", "didn't", "do", "does", "doesn't", "doing",
            "don't", "down", "during", "each", "few", "for", "from", "further", "had", "hadn't", "has", "hasn't",
            "have", "haven't", "having", "he", "he'd", "he'll", "he's", "her", "here", "here's", "hers", "herself",
            "him", "himself", "his", "how", "how's", "i", "i'd", "i'll", "i'm", "i've", "if", "in", "into", "is",
            "isn't", "it", "it's", "its", "itself", "let's", "me", "more", "most", "mustn't", "my", "myself", "no",
            "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought", "our", "ours", "ourselves",
            "out", "over", "own", "same", "shan't", "she", "she'd", "she'll", "she's", "should", "shouldn't", "so",
            "some", "such", "than", "that", "that's", "the", "their", "theirs", "them", "themselves", "then", "there",
            "there's", "these", "they", "they'd", "they'll", "they're", "they've", "this", "those", "through", "to",
            "too", "under", "until", "up", "very", "was", "wasn't", "we", "we'd", "we'll", "we're", "we've", "were",
            "weren't", "what", "what's", "when", "when's", "where", "where's", "which", "while", "who", "who's", "whom",
            "why", "why's", "with", "won't", "would", "wouldn't", "you", "you'd", "you'll", "you're", "you've", "your",
            "yours", "yourself", "yourselves"
    );

    private static final Set<String> INVALID_CANDIDATES = Set.of(
            "experience", "experiences", "project", "projects", "work", "worked", "working", "job", "role", "roles",
            "developer", "developers", "engineer", "engineers", "engineering", "responsibilities", "responsible",
            "developed", "developing", "built", "building", "created", "creating", "managed", "managing", "management",
            "implemented", "implementing", "using", "used", "knowledge", "skills", "skill", "technologies", "technology",
            "tools", "tool", "years", "year", "month", "months", "etc", "various", "multiple", "candidate", "intern",
            "internship", "description", "summary", "profile", "overview", "details"
    );

    private static final Map<String, String> CANONICAL = buildCanonical();

    private static Map<String, String> buildCanonical() {
        Map<String, String> m = new HashMap<>();

        // 1. Languages & Runtimes
        add(m, "C", "c");
        add(m, "R", "r");
        add(m, "Go", "go", "golang");
        add(m, "C++", "c++", "cpp", "c/c++");
        add(m, "C#", "c#", "csharp", "c-sharp");
        add(m, "Java", "java");
        add(m, "Python", "python", "python3", "py");
        add(m, "Kotlin", "kotlin");
        add(m, "Rust", "rust");
        add(m, "PHP", "php");
        add(m, "Ruby", "ruby");
        add(m, "Scala", "scala");
        add(m, "Dart", "dart");
        add(m, "SQL", "sql");
        add(m, "JavaScript", "javascript", "js");
        add(m, "TypeScript", "typescript", "ts");
        add(m, "Node.js", "node.js", "nodejs", "node");

        // 2. Frontend Frameworks & UI
        add(m, "HTML", "html", "html5");
        add(m, "CSS", "css", "css3");
        add(m, "HTML/CSS", "html/css", "html & css", "html and css");
        add(m, "React", "react", "react.js", "reactjs", "react-js");
        add(m, "React Native", "react native", "react-native");
        add(m, "Angular", "angular", "angularjs", "angular.js");
        add(m, "Vue", "vue", "vue.js", "vuejs", "vue-js");
        add(m, "Next.js", "next.js", "nextjs");
        add(m, "Tailwind CSS", "tailwind css", "tailwind", "tailwindcss");
        add(m, "Bootstrap", "bootstrap");
        add(m, "Sass", "sass", "scss");
        add(m, "Redux", "redux", "redux-toolkit");
        add(m, "jQuery", "jquery");

        // 3. Backend Frameworks
        add(m, "Spring Boot", "spring boot", "spring", "springboot", "spring-boot");
        add(m, "Express", "express", "express.js", "expressjs");
        add(m, "Django", "django");
        add(m, "Flask", "flask");
        add(m, "FastAPI", "fastapi");
        add(m, "ASP.NET", "asp.net", "asp.net core", "aspnet");
        add(m, ".NET", ".net", "dotnet", ".net core");
        add(m, "Ruby on Rails", "ruby on rails", "rails");
        add(m, "Laravel", "laravel");

        // 4. Databases & Storage
        add(m, "PostgreSQL", "postgresql", "postgres");
        add(m, "MySQL", "mysql");
        add(m, "MongoDB", "mongodb", "mongo");
        add(m, "Redis", "redis");
        add(m, "SQLite", "sqlite", "sqlite3");
        add(m, "Oracle", "oracle", "oracle database");
        add(m, "Microsoft SQL Server", "microsoft sql server", "mssql", "sql server");
        add(m, "Cassandra", "cassandra");
        add(m, "Elasticsearch", "elasticsearch", "elastic search");
        add(m, "Relational Databases", "relational databases", "relational database");
        add(m, "NoSQL", "nosql", "nosql database");

        // 5. Cloud & DevOps
        add(m, "Docker", "docker");
        add(m, "Kubernetes", "kubernetes", "k8s");
        add(m, "AWS", "aws", "amazon web services");
        add(m, "Azure", "azure", "microsoft azure");
        add(m, "GCP", "gcp", "google cloud", "google cloud platform");
        add(m, "Git", "git");
        add(m, "GitHub", "github", "git hub");
        add(m, "GitLab", "gitlab", "git lab");
        add(m, "Linux", "linux");
        add(m, "Unix", "unix");
        add(m, "Jenkins", "jenkins");
        add(m, "Terraform", "terraform");
        add(m, "Ansible", "ansible");
        add(m, "CI/CD", "ci/cd", "cicd", "ci / cd");

        // 6. AI / ML / Data Science / Analytics
        add(m, "Machine Learning", "machine learning", "ml");
        add(m, "Deep Learning", "deep learning", "dl");
        add(m, "Artificial Intelligence", "artificial intelligence", "ai");
        add(m, "Data Science", "data science");
        add(m, "Data Engineering", "data engineering");
        add(m, "Data Analysis", "data analysis", "data analytics");
        add(m, "Natural Language Processing", "natural language processing", "nlp");
        add(m, "Computer Vision", "computer vision", "cv");
        add(m, "TensorFlow", "tensorflow");
        add(m, "PyTorch", "pytorch");
        add(m, "Pandas", "pandas");
        add(m, "NumPy", "numpy");
        add(m, "Power BI", "power bi", "powerbi", "power-bi");
        add(m, "Tableau", "tableau");

        // 7. Testing & Quality Assurance
        add(m, "JUnit", "junit", "junit5", "junit 5");
        add(m, "Selenium", "selenium", "selenium webdriver");
        add(m, "Jest", "jest");
        add(m, "Cypress", "cypress");
        add(m, "Postman", "postman");
        add(m, "Unit Testing", "unit testing", "unit test", "unit tests");

        // 8. Architecture & Professional Skills
        add(m, "REST APIs", "rest apis", "rest api", "rest", "restful api", "restful apis");
        add(m, "GraphQL", "graphql");
        add(m, "Microservices", "microservices", "microservice");
        add(m, "Agile", "agile", "agile methodology");
        add(m, "Scrum", "scrum");
        add(m, "Cybersecurity", "cybersecurity", "cyber security");
        add(m, "Object-Oriented Programming", "object-oriented programming", "oop");
        add(m, "System Design", "system design");

        return Collections.unmodifiableMap(m);
    }

    private static void add(Map<String, String> map, String display, String... keys) {
        map.put(display.toLowerCase(Locale.ROOT), display);
        for (String k : keys) {
            map.put(k.toLowerCase(Locale.ROOT), display);
        }
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
