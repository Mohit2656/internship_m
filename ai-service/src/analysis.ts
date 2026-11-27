import { existsSync, readFileSync, statSync } from "node:fs";
import { extname, resolve } from "node:path";
import { extractTextFromDocx, extractTextFromPdf, parseResume, type ParsedResume } from "resume-extract";
import { loadRuntime } from "resume-extract/src/runtime";
import { normalizeSkills, scanSkillsInText } from "./skill-catalog.js";

export { normalizeSkills };

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

const MIN_EXTRACTABLE_TEXT_LENGTH = 20;

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

export function toAnalysisResult(parsed: ParsedResume, modelPath: string, extractedText?: string): ResumeAnalysisResult {
  const dictionarySkills = scanSkillsInText(extractedText ?? parsed._rawText ?? "");
  return {
    modelName: process.env.RESUME_MODEL_NAME || "oksomu/resume-ner",
    modelVersion: modelVersion(modelPath),
    skills: normalizeSkills([...parsed.skills, ...dictionarySkills]),
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
  const normalizedText = text.trim();
  if (normalizedText.length < MIN_EXTRACTABLE_TEXT_LENGTH || !/[a-z0-9]/i.test(normalizedText)) {
    throw new Error("INSUFFICIENT_EXTRACTABLE_TEXT");
  }
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
  const result = toAnalysisResult(parsed, modelPath, text);
  reportStage?.("response_preparation_completed");
  return result;
}
