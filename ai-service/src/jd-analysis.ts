import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { pipeline } from "@huggingface/transformers";

export type SkillCandidate = { name: string; normalizedName: string };
export type OpportunityAnalysisResult = {
  modelName: string;
  modelVersion: string;
  technicalSkills: SkillCandidate[];
  requiredSkillSuggestions: SkillCandidate[];
  preferredSkillSuggestions: SkillCandidate[];
  roleSignals: string[];
  responsibilitySignals: string[];
};
export type SemanticEmbedder = (texts: string[]) => Promise<number[][]>;

const modelName = "onnx-community/all-MiniLM-L6-v2-ONNX";
const skills = new Map<string, string>([
  ["java", "Java"], ["kotlin", "Kotlin"], ["python", "Python"], ["c++", "C++"], ["c#", "C#"],
  ["javascript", "JavaScript"], ["typescript", "TypeScript"], ["spring boot", "Spring Boot"],
  ["node.js", "Node.js"], ["react", "React"], ["angular", "Angular"], ["rest api", "REST APIs"],
  ["rest APIs", "REST APIs"], ["graphql", "GraphQL"], ["postgresql", "PostgreSQL"], ["mysql", "MySQL"],
  ["relational database", "Relational Databases"], ["mongodb", "MongoDB"], ["redis", "Redis"],
  ["docker", "Docker"], ["kubernetes", "Kubernetes"], ["aws", "AWS"], ["azure", "Azure"], ["gcp", "GCP"],
  ["git", "Git"], ["github", "GitHub"], ["linux", "Linux"], ["jenkins", "Jenkins"], ["terraform", "Terraform"],
  ["machine learning", "Machine Learning"], ["data engineering", "Data Engineering"], ["cybersecurity", "Cybersecurity"]
].map(([key, value]) => [key.toLowerCase(), value]));
const roleLabels = [
  ["Backend Development", "backend software engineering, server-side APIs and scalable services"],
  ["Frontend Development", "frontend web interfaces and client-side application development"],
  ["Cloud Engineering", "cloud platforms, infrastructure and distributed systems"],
  ["DevOps", "DevOps automation, deployment, containers and continuous delivery"],
  ["Data Engineering", "data pipelines, data platforms and analytics engineering"],
  ["Mobile Development", "mobile application development for Android or iOS"],
  ["Cybersecurity", "cybersecurity, information security and threat protection"]
] as const;

let cachedPath = "";
let cachedEmbedder: Promise<SemanticEmbedder> | null = null;

export async function localEmbedder(path: string): Promise<SemanticEmbedder> {
  if (cachedEmbedder && cachedPath === path) return cachedEmbedder;
  cachedPath = path;
  cachedEmbedder = (async () => {
    const extractor = await pipeline("feature-extraction", path, { local_files_only: true, dtype: "q4" });
    return async (texts: string[]) => {
      const output = await extractor(texts, { pooling: "mean", normalize: true });
      return output.tolist() as number[][];
    };
  })();
  return cachedEmbedder;
}

export function semanticModelVersion(path: string) {
  const configured = process.env.JD_MODEL_VERSION?.trim();
  if (configured) return configured;
  const file = resolve(path, ".commit-sha");
  return existsSync(file) ? readFileSync(file, "utf8").trim() || "unknown" : "unknown";
}
function sentences(value: string) {
  return value.split(/(?<=[.!?])\s+|[\r\n]+/).map(item => item.trim()).filter(Boolean);
}
function detectSkills(sentence: string): SkillCandidate[] {
  const lower = sentence.toLowerCase(); const found: SkillCandidate[] = [];
  for (const [needle, display] of skills) {
    const escaped = needle.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const plural = needle === "rest api" || needle === "relational database" ? "s?" : "";
    if (new RegExp(`(^|[^a-z0-9+#.])${escaped}${plural}([^a-z0-9+#.]|$)`, "i").test(lower))
      found.push({ name: display, normalizedName: display.toLowerCase() });
  }
  return found;
}
function unique(values: SkillCandidate[]) {
  return [...new Map(values.map(value => [value.normalizedName, value])).values()];
}
function cosine(a: number[], b: number[]) { return a.reduce((sum, value, index) => sum + value * b[index], 0); }
function conciseResponsibility(sentence: string) {
  return sentence.replace(/^(the\s+)?(intern|candidate|you)\s+(will|would)\s+/i, "")
    .replace(/^(responsibilities\s*(include|:)?\s*)/i, "").trim().replace(/[.!?]+$/, "");
}

export async function analyzeOpportunity(title: string, description: string, modelPath: string, injected?: SemanticEmbedder): Promise<OpportunityAnalysisResult> {
  const cleanTitle = title.trim(); const cleanDescription = description.trim();
  if (!cleanTitle || !cleanDescription) throw new Error("EMPTY_JOB_DESCRIPTION");
  const parts = sentences(cleanDescription);
  const technical: SkillCandidate[] = []; const required: SkillCandidate[] = []; const preferred: SkillCandidate[] = [];
  for (const part of parts) {
    const candidates = detectSkills(part); technical.push(...candidates);
    const lower = part.toLowerCase();
    if (/\b(preferred|nice to have|bonus|advantage|a plus)\b/.test(lower)) preferred.push(...candidates);
    else if (/\b(required|must|should|strong|proficien|knowledge of|experience with|familiarity with)\b/.test(lower)) required.push(...candidates);
  }

  const embed = injected || await localEmbedder(modelPath);
  const roleTexts = roleLabels.map(value => value[1]);
  const responsibilityAnchor = "specific work responsibility or task the intern will perform";
  const vectors = await embed([`${cleanTitle}. ${cleanDescription}`, ...roleTexts, responsibilityAnchor, ...parts]);
  const documentVector = vectors[0];
  const roleSignals = roleLabels.filter((_, index) => cosine(documentVector, vectors[index + 1]) >= 0.45).map(value => value[0]).slice(0, 4);
  const anchorIndex = 1 + roleTexts.length;
  const responsibilitySignals = parts.filter((part, index) => {
    const actionLanguage = /\b(build|develop|design|implement|maintain|create|support|analy[sz]e|collaborate|work with|deploy|test|optimize|manage)\b/i.test(part);
    return actionLanguage && cosine(vectors[anchorIndex], vectors[anchorIndex + 1 + index]) >= 0.22;
  }).map(conciseResponsibility).filter(value => value.length >= 5 && value.length <= 300).slice(0, 8);

  return {
    modelName, modelVersion: semanticModelVersion(modelPath), technicalSkills: unique(technical),
    requiredSkillSuggestions: unique(required), preferredSkillSuggestions: unique(preferred),
    roleSignals: [...new Set(roleSignals)], responsibilitySignals: [...new Set(responsibilitySignals)]
  };
}
