import { api, ApiError } from "@/lib/api";
import axios from "axios";

export type ResumeParsingStatus = "NOT_PARSED" | "PENDING" | "PARSED" | "FAILED";

export type StudentResume = {
  id: number;
  originalFilename: string;
  contentType: string;
  fileSize: number;
  uploadedAt: string;
  active: boolean;
  parsingStatus: ResumeParsingStatus;
};

export type ProfessionalProfile = {
  id: number | null;
  headline: string | null;
  careerInterests: string | null;
  resume: StudentResume | null;
  createdAt: string | null;
  updatedAt: string | null;
  skillsUpdatedAt: string | null;
};

export type ResumeAnalysis = {
  resumeId: number;
  status: ResumeParsingStatus;
  modelName: string | null;
  modelVersion: string | null;
  startedAt: string | null;
  completedAt: string | null;
  skills: string[];
  experience: { title: string | null; company: string | null; startDate: string | null; endDate: string | null }[];
  education: { degree: string | null; field: string | null; institution: string | null }[];
  certifications: string[];
};

export function notParsedAnalysis(resumeId: number): ResumeAnalysis {
  return { resumeId, status: "NOT_PARSED", modelName: null, modelVersion: null, startedAt: null,
    completedAt: null, skills: [], experience: [], education: [], certifications: [] };
}

export type SkillProfile = {
  skills: { id: number; displayName: string; source: "AI_EXTRACTED" | "STUDENT_ADDED" }[];
  suggestions: { id: number; displayName: string }[];
};

export const resumeMaxBytes = 5 * 1024 * 1024;
export const resumeAccept = ".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document";

export function resumeError(cause: unknown, fallback: string) {
  return axios.isAxiosError<ApiError>(cause) ? cause.response?.data?.message ?? fallback : fallback;
}

export async function downloadResume(url: string, fileName: string) {
  const response = await api.get<Blob>(url, { responseType: "blob" });
  const objectUrl = URL.createObjectURL(response.data);
  const anchor = document.createElement("a");
  anchor.href = objectUrl;
  anchor.download = fileName;
  anchor.click();
  URL.revokeObjectURL(objectUrl);
}

export function readableFileSize(bytes: number) {
  return bytes < 1024 * 1024 ? `${Math.ceil(bytes / 1024)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}
