import assert from "node:assert/strict";
import { existsSync } from "node:fs";
import { resolve } from "node:path";
import { test } from "node:test";
import { Document, Packer, Paragraph } from "docx";
import { PDFDocument, StandardFonts } from "pdf-lib";
import { analyzeResume } from "../src/analysis.js";
import { analyzeOpportunity, localEmbedder } from "../src/jd-analysis.js";
import { matchOpportunity } from "../src/opportunity-matching.js";

const modelPath = resolve(process.env.RESUME_MODEL_PATH || "./models/resume-ner");
const enabled = process.env.AI_REAL_MODEL_TEST === "true" && existsSync(resolve(modelPath, "onnx/model_quantized.onnx"));
const jdModelPath = resolve(process.env.JD_MODEL_PATH || "./models/jd-embedding");
const jdEnabled = process.env.AI_REAL_MODEL_TEST === "true" && existsSync(resolve(jdModelPath, "onnx/model_q4.onnx"));
const text = `RAJESH KUMAR\nEXPERIENCE\nSenior Software Engineer\nInfosys\nApril 2020 - Present\nSoftware Engineer\nTCS\nJune 2016 - March 2020\nEDUCATION\nBachelor of Technology in Computer Engineering\nSt. Vincent Pallotti College of Engineering & Technology\nSKILLS\nJava, Python, PostgreSQL, Docker\nCERTIFICATIONS\nAWS Certified Cloud Practitioner`;

async function pdf(): Promise<Uint8Array> {
  const document = await PDFDocument.create();
  const page = document.addPage([612, 792]);
  const font = await document.embedFont(StandardFonts.Helvetica);
  page.drawText(text, { x: 40, y: 740, size: 10, font, lineHeight: 15 });
  return document.save();
}

async function docx(): Promise<Uint8Array> {
  const document = new Document({ sections: [{ children: text.split("\n").map((line) => new Paragraph(line)) }] });
  return new Uint8Array(await Packer.toBuffer(document));
}

test("real DistilBERT ONNX path analyzes PDF", { skip: !enabled, timeout: 120_000 }, async () => {
  const result = await analyzeResume(await pdf(), "resume.pdf", "application/pdf", modelPath);
  assert.equal(result.modelName, "oksomu/resume-ner");
  assert.ok(result.skills.length > 0);
  assert.ok(result.experience.length > 0);
  assert.ok(result.education.length > 0);
});

test("real DistilBERT ONNX path analyzes DOCX", { skip: !enabled, timeout: 120_000 }, async () => {
  const result = await analyzeResume(await docx(), "resume.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", modelPath);
  assert.ok(result.skills.length > 0);
  assert.ok(result.experience.length > 0);
  assert.ok(result.education.length > 0);
  assert.ok(result.certifications.length > 0);
});

test("real MiniLM ONNX path semantically analyzes a job description", { skip: !jdEnabled, timeout: 120_000 }, async () => {
  const result = await analyzeOpportunity("Software Engineering Intern",
    "Candidates should have experience with Java or Kotlin. Knowledge of Spring Boot and REST APIs is required. Experience with Docker or AWS is preferred. The intern will build scalable backend services.",
    jdModelPath);
  assert.equal(result.modelName, "onnx-community/all-MiniLM-L6-v2-ONNX");
  assert.ok(result.technicalSkills.some(value => value.name === "Spring Boot"));
  assert.ok(result.requiredSkillSuggestions.some(value => value.name === "Java"));
  assert.ok(result.preferredSkillSuggestions.some(value => value.name === "Docker"));
  assert.ok(result.roleSignals.length > 0);
  assert.ok(result.responsibilitySignals.length > 0);
});

test("real MiniLM ONNX path produces conservative matching evidence", { skip: !jdEnabled, timeout: 120_000 }, async () => {
  const result = await matchOpportunity({
    student: { skills: [{ name: "JAVA", normalizedName: "java" }, { name: "MySQL", normalizedName: "mysql" }, { name: "JavaScript", normalizedName: "javascript" }], headline: "Backend software developer", careerInterests: ["Cloud engineering"], experience: ["Built REST API services"] },
    opportunity: { title: "Backend Intern", description: "Build APIs", requiredSkills: [{ name: "Java", normalizedName: "java" }, { name: "PostgreSQL", normalizedName: "postgresql" }], preferredSkills: [], roleSignals: ["Backend Development"], responsibilitySignals: ["Develop REST API services"] }
  }, jdModelPath);
  assert.equal(result.requiredSkillMatches[0].matchType, "EXACT");
  assert.equal(result.requiredSkillMatches[1].matchType, "MISSING");
  assert.ok(result.roleEvidence.length > 0); assert.ok(result.experienceEvidence.length > 0);
});

test("real MiniLM does not treat JavaScript as Java", { skip: !jdEnabled, timeout: 120_000 }, async () => {
  const result = await matchOpportunity({
    student: { skills: [{ name: "JavaScript", normalizedName: "javascript" }], headline: "", careerInterests: [], experience: [] },
    opportunity: { title: "Java Intern", description: "Java required", requiredSkills: [{ name: "Java", normalizedName: "java" }], preferredSkills: [], roleSignals: [], responsibilitySignals: [] }
  }, jdModelPath);
  assert.equal(result.requiredSkillMatches[0].matchType, "MISSING");
});

test("real MiniLM loader reuses the cached model", { skip: !jdEnabled, timeout: 120_000 }, async () => {
  const first = await localEmbedder(jdModelPath); const second = await localEmbedder(jdModelPath);
  assert.equal(first, second);
});
