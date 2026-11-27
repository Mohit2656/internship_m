import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { pipeline } from "@huggingface/transformers";
import { scanSkillsInText } from "./skill-catalog.js";

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
  return scanSkillsInText(sentence).map(display => ({
    name: display,
    normalizedName: display.toLowerCase()
  }));
}
type JdSection = "NEUTRAL" | "REQUIRED" | "PREFERRED" | "RESPONSIBILITIES";
function detectSectionHeader(line: string): JdSection | null {
  const cleaned = line.replace(/^[-*•\d.)\s]+/, "").trim().toLowerCase();
  if (cleaned.length > 80) return null;
  if (/^(preferred|nice to have|bonus|good to have|optional)\b/.test(cleaned) && /[:\-]?$/.test(cleaned)) return "PREFERRED";
  if (/^(required|requirements|must have|qualifications|minimum qualifications|technical skills|skills required)\b/.test(cleaned)) return "REQUIRED";
  if (/^(responsibilities|what you will do|key responsibilities|role overview)\b/.test(cleaned)) return "RESPONSIBILITIES";
  return null;
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
  let activeSection: JdSection = "NEUTRAL";
  for (const part of parts) {
    const header = detectSectionHeader(part);
    if (header) activeSection = header;
    const candidates = detectSkills(part); technical.push(...candidates);
    const lower = part.toLowerCase();
    if (/\b(preferred|nice to have|bonus|advantage|a plus|good to have)\b/.test(lower)) preferred.push(...candidates);
    else if (/\b(required|must|should|strong|proficien|knowledge of|experience with|familiarity with)\b/.test(lower)) required.push(...candidates);
    else if (activeSection === "PREFERRED") preferred.push(...candidates);
    else if (activeSection === "REQUIRED") required.push(...candidates);
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
