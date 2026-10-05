import { existsSync, readFileSync } from "node:fs";
import { extname, resolve } from "node:path";
import { parseResumeDocx, parseResumePdf, type ParsedResume } from "resume-extract";

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

const canonicalSkills = new Map([
  ["java", "Java"], ["python", "Python"], ["postgresql", "PostgreSQL"], ["mysql", "MySQL"],
  ["spring boot", "Spring Boot"], ["javascript", "JavaScript"], ["typescript", "TypeScript"],
  ["html", "HTML"], ["css", "CSS"], ["html/css", "HTML/CSS"], ["docker", "Docker"],
  ["github", "GitHub"], ["node.js", "Node.js"], ["c++", "C++"], ["c#", "C#"]
]);

export function normalizeSkills(values: string[]): string[] {
  const seen = new Set<string>();
  const normalized: string[] = [];
  for (const value of values) {
    const clean = value.trim().replace(/\s+/g, " ");
    if (!clean) continue;
    const candidate = canonicalSkills.get(clean.toLowerCase()) ?? clean;
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

export async function analyzeResume(bytes: Uint8Array, filename: string, contentType: string, modelPath: string) {
  const extension = extname(filename).toLowerCase();
  let parsed: ParsedResume;
  if (contentType === "application/pdf" && extension === ".pdf") {
    parsed = await parseResumePdf(bytes, modelPath);
  } else if (contentType === "application/vnd.openxmlformats-officedocument.wordprocessingml.document" && extension === ".docx") {
    parsed = await parseResumeDocx(bytes, modelPath);
  } else {
    throw new Error("UNSUPPORTED_DOCUMENT");
  }
  return toAnalysisResult(parsed, modelPath);
}
