import { existsSync, readFileSync, statSync } from "node:fs";
import { extname, resolve } from "node:path";
import { extractTextFromDocx, extractTextFromPdf, parseResume, type ParsedResume } from "resume-extract";
import { loadRuntime } from "resume-extract/src/runtime";

export type ExtractedExperience = { title: string | null; company: string | null; startDate: string | null; endDate: string | null };
export type ExtractedEducation = { degree: string | null; field: string | null; institution: string | null };
export type ResumeAnalysisResult = {
  modelName: string;
  modelVersion: string;
  skills: string[];
  experience: ExtractedExperience[];
  education: ExtractedEducation[];
  certifications: string[];
};

const canonicalSkills = new Map<string, string>([
  // Legitimate short skills
  ["c", "C"],
  ["r", "R"],
  ["go", "Go"],
  ["golang", "Go"],
  ["c++", "C++"],
  ["cpp", "C++"],
  ["c#", "C#"],
  ["csharp", "C#"],

  // Languages & Runtimes
  ["java", "Java"],
  ["python", "Python"],
  ["python3", "Python"],
  ["py", "Python"],
  ["kotlin", "Kotlin"],
  ["rust", "Rust"],
  ["php", "PHP"],
  ["ruby", "Ruby"],
  ["swift", "Swift"],
  ["scala", "Scala"],
  ["dart", "Dart"],
  ["javascript", "JavaScript"],
  ["js", "JavaScript"],
  ["typescript", "TypeScript"],
  ["ts", "TypeScript"],
  ["node.js", "Node.js"],
  ["nodejs", "Node.js"],
  ["node", "Node.js"],

  // Frontend & UI
  ["html", "HTML"],
  ["html5", "HTML"],
  ["css", "CSS"],
  ["css3", "CSS"],
  ["html/css", "HTML/CSS"],
  ["html & css", "HTML/CSS"],
  ["html and css", "HTML/CSS"],
  ["react", "React"],
  ["react.js", "React"],
  ["reactjs", "React"],
  ["react-js", "React"],
  ["react native", "React Native"],
  ["react-native", "React Native"],
  ["angular", "Angular"],
  ["angularjs", "Angular"],
  ["angular.js", "Angular"],
  ["vue", "Vue"],
  ["vue.js", "Vue"],
  ["vuejs", "Vue"],
  ["vue-js", "Vue"],
  ["next.js", "Next.js"],
  ["nextjs", "Next.js"],
  ["tailwind", "Tailwind CSS"],
  ["tailwind css", "Tailwind CSS"],
  ["tailwindcss", "Tailwind CSS"],
  ["bootstrap", "Bootstrap"],
  ["sass", "Sass"],
  ["scss", "Sass"],
  ["redux", "Redux"],
  ["redux-toolkit", "Redux"],
  ["jquery", "jQuery"],

  // Backend Frameworks
  ["spring boot", "Spring Boot"],
  ["spring", "Spring Boot"],
  ["springboot", "Spring Boot"],
  ["spring-boot", "Spring Boot"],
  ["express", "Express"],
  ["express.js", "Express"],
  ["expressjs", "Express"],
  ["django", "Django"],
  ["flask", "Flask"],
  ["fastapi", "FastAPI"],
  ["asp.net", "ASP.NET"],
  ["asp.net core", "ASP.NET"],
  ["aspnet", "ASP.NET"],
  [".net", ".NET"],
  ["dotnet", ".NET"],
  [".net core", ".NET"],
  ["ruby on rails", "Ruby on Rails"],
  ["rails", "Ruby on Rails"],
  ["laravel", "Laravel"],

  // Databases & Storage
  ["sql", "SQL"],
  ["postgresql", "PostgreSQL"],
  ["postgres", "PostgreSQL"],
  ["mysql", "MySQL"],
  ["mongodb", "MongoDB"],
  ["mongo", "MongoDB"],
  ["redis", "Redis"],
  ["sqlite", "SQLite"],
  ["sqlite3", "SQLite"],
  ["oracle", "Oracle"],
  ["oracle database", "Oracle"],
  ["microsoft sql server", "Microsoft SQL Server"],
  ["mssql", "Microsoft SQL Server"],
  ["sql server", "Microsoft SQL Server"],
  ["cassandra", "Cassandra"],
  ["elasticsearch", "Elasticsearch"],
  ["elastic search", "Elasticsearch"],
  ["relational database", "Relational Databases"],
  ["relational databases", "Relational Databases"],
  ["nosql", "NoSQL"],
  ["nosql database", "NoSQL"],

  // Cloud & DevOps
  ["docker", "Docker"],
  ["kubernetes", "Kubernetes"],
  ["k8s", "Kubernetes"],
  ["aws", "AWS"],
  ["amazon web services", "AWS"],
  ["azure", "Azure"],
  ["microsoft azure", "Azure"],
  ["gcp", "GCP"],
  ["google cloud", "GCP"],
  ["google cloud platform", "GCP"],
  ["git", "Git"],
  ["github", "GitHub"],
  ["git hub", "GitHub"],
  ["gitlab", "GitLab"],
  ["git lab", "GitLab"],
  ["linux", "Linux"],
  ["unix", "Unix"],
  ["jenkins", "Jenkins"],
  ["terraform", "Terraform"],
  ["ansible", "Ansible"],
  ["ci/cd", "CI/CD"],
  ["cicd", "CI/CD"],
  ["ci / cd", "CI/CD"],

  // AI & Data
  ["machine learning", "Machine Learning"],
  ["ml", "Machine Learning"],
  ["deep learning", "Deep Learning"],
  ["dl", "Deep Learning"],
  ["artificial intelligence", "Artificial Intelligence"],
  ["ai", "Artificial Intelligence"],
  ["data science", "Data Science"],
  ["data engineering", "Data Engineering"],
  ["data analysis", "Data Analysis"],
  ["data analytics", "Data Analysis"],
  ["natural language processing", "Natural Language Processing"],
  ["nlp", "Natural Language Processing"],
  ["computer vision", "Computer Vision"],
  ["cv", "Computer Vision"],
  ["tensorflow", "TensorFlow"],
  ["pytorch", "PyTorch"],
  ["pandas", "Pandas"],
  ["numpy", "NumPy"],
  ["power bi", "Power BI"],
  ["powerbi", "Power BI"],
  ["power-bi", "Power BI"],
  ["tableau", "Tableau"],

  // Testing & QA
  ["junit", "JUnit"],
  ["junit5", "JUnit"],
  ["junit 5", "JUnit"],
  ["selenium", "Selenium"],
  ["selenium webdriver", "Selenium"],
  ["jest", "Jest"],
  ["cypress", "Cypress"],
  ["postman", "Postman"],
  ["unit testing", "Unit Testing"],
  ["unit test", "Unit Testing"],
  ["unit tests", "Unit Testing"],

  // Architecture & Professional Skills
  ["rest api", "REST APIs"],
  ["rest apis", "REST APIs"],
  ["rest", "REST APIs"],
  ["restful api", "REST APIs"],
  ["restful apis", "REST APIs"],
  ["graphql", "GraphQL"],
  ["microservices", "Microservices"],
  ["microservice", "Microservices"],
  ["agile", "Agile"],
  ["agile methodology", "Agile"],
  ["scrum", "Scrum"],
  ["cybersecurity", "Cybersecurity"],
  ["cyber security", "Cybersecurity"],
  ["object-oriented programming", "Object-Oriented Programming"],
  ["oop", "Object-Oriented Programming"],
  ["system design", "System Design"]
]);

const stopwords = new Set([
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
]);

const invalidCandidates = new Set([
  "experience", "experiences", "project", "projects", "work", "worked", "working", "job", "role", "roles",
  "developer", "developers", "engineer", "engineers", "engineering", "responsibilities", "responsible",
  "developed", "developing", "built", "building", "created", "creating", "managed", "managing", "management",
  "implemented", "implementing", "using", "used", "knowledge", "skills", "skill", "technologies", "technology",
  "tools", "tool", "years", "year", "month", "months", "etc", "various", "multiple", "candidate", "intern",
  "internship", "description", "summary", "profile", "overview", "details"
]);

export function normalizeSkills(values: string[]): string[] {
  const seen = new Set<string>();
  const normalized: string[] = [];
  for (const value of values) {
    if (typeof value !== "string") continue;
    const clean = value.trim().replace(/\s+/g, " ");
    if (!clean || clean.length > 100) continue;
    const lower = clean.toLowerCase();

    // Filter common stopwords, narrative words, and non-alphanumeric tokens
    if (stopwords.has(lower) || invalidCandidates.has(lower)) continue;
    if (!/[a-z0-9]/i.test(clean)) continue;

    // Validate extracted skills against the canonical skill catalog and recognized aliases
    const candidate = canonicalSkills.get(lower);
    if (!candidate) continue;

    // Deduplicate and normalize recognized skills
    const key = candidate.toLocaleLowerCase("en-US");
    if (!seen.has(key)) {
      seen.add(key);
      normalized.push(candidate);
    }
  }
  if (seen.has("html/css")) return normalized.filter((skill) => !["html", "css"].includes(skill.toLowerCase()));
  return normalized;
}

function clean(value: string | undefined): string | null {
  const normalized = value?.trim().replace(/\s+/g, " ");
  return normalized || null;
}

function modelVersion(modelPath: string): string {
  const configured = process.env.RESUME_MODEL_VERSION?.trim();
  if (configured) return configured;
  const shaPath = resolve(modelPath, ".commit-sha");
  if (!existsSync(shaPath)) return "unknown";
  return readFileSync(shaPath, "utf8").trim() || "unknown";
}

export function toAnalysisResult(parsed: ParsedResume, modelPath: string): ResumeAnalysisResult {
  return {
    modelName: process.env.RESUME_MODEL_NAME || "oksomu/resume-ner",
    modelVersion: modelVersion(modelPath),
    skills: normalizeSkills(parsed.skills),
    experience: parsed.experience.map((item) => ({
      title: clean(item.title), company: clean(item.company), startDate: clean(item.start_date), endDate: clean(item.end_date)
    })),
    education: parsed.education.map((item) => ({
      degree: clean(item.degree), field: clean(item.field), institution: clean(item.institution)
    })),
    certifications: [...new Map(parsed.certifications.map((item) => [item.trim().toLowerCase(), item.trim()])).values()].filter(Boolean)
  };
}

export type ResumeAnalysisStage = "extraction_started" | "extraction_completed" | "model_loading_started"
  | "model_initialization_started" | "model_initialization_completed" | "model_loading_completed" | "tokenizer_ready"
  | "model_inference_started" | "model_inference_completed" | "response_preparation_started" | "response_preparation_completed";
export type ResumeAnalysisMetadata = {
  modelFiles?: { filename: string; available: boolean; sizeBytes?: number }[];
  tokenizerReady?: boolean;
};

function modelFileMetadata(modelPath: string): NonNullable<ResumeAnalysisMetadata["modelFiles"]> {
  return ["resume_config.json", "companies.json", "config.json", "tokenizer.json", "onnx/model_quantized.onnx"].map(filename => {
    try {
      const stat = statSync(resolve(modelPath, filename));
      return { filename, available: stat.isFile(), sizeBytes: stat.size };
    } catch { return { filename, available: false }; }
  });
}

export async function analyzeResume(bytes: Uint8Array, filename: string, contentType: string, modelPath: string,
    reportStage?: (stage: ResumeAnalysisStage, metadata?: ResumeAnalysisMetadata) => void,
    registerSensitiveText?: (text: string) => void) {
  const extension = extname(filename).toLowerCase();
  let text: string;
  // These are the same extraction and parsing calls used by resume-extract's PDF/DOCX wrappers.
  if (contentType === "application/pdf" && extension === ".pdf") {
    reportStage?.("extraction_started");
    text = await extractTextFromPdf(bytes);
  } else if (contentType === "application/vnd.openxmlformats-officedocument.wordprocessingml.document" && extension === ".docx") {
    reportStage?.("extraction_started");
    text = await extractTextFromDocx(bytes);
  } else {
    throw new Error("UNSUPPORTED_DOCUMENT");
  }
  registerSensitiveText?.(text);
  reportStage?.("extraction_completed");
  reportStage?.("model_loading_started", { modelFiles: modelFileMetadata(modelPath) });
  reportStage?.("model_initialization_started");
  // Use the parser's own cached initializer; parseResume below reuses this exact same runtime.
  const runtime = await loadRuntime(modelPath);
  reportStage?.("model_initialization_completed");
  reportStage?.("model_loading_completed");
  reportStage?.("tokenizer_ready", { tokenizerReady: runtime.tokenizer !== null });
  reportStage?.("model_inference_started");
  const parsed = await parseResume(text, modelPath);
  reportStage?.("model_inference_completed");
  for (const value of Object.values(parsed.personal)) if (typeof value === "string") registerSensitiveText?.(value);
  reportStage?.("response_preparation_started");
  const result = toAnalysisResult(parsed, modelPath);
  reportStage?.("response_preparation_completed");
  return result;
}
