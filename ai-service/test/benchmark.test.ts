import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { test } from "node:test";
import { toAnalysisResult } from "../src/analysis.js";
import { analyzeOpportunity } from "../src/jd-analysis.js";
import { scanSkillsInText } from "../src/skill-catalog.js";

const backendCatalogPath = resolve(process.cwd(), "../backend/src/main/resources/skills-catalog.json");

test("shared skills catalog is identical between ai-service and backend", { skip: !existsSync(backendCatalogPath) }, () => {
  const aiCatalog = readFileSync(resolve(process.cwd(), "src/skills-catalog.json"), "utf8").replace(/\r\n/g, "\n");
  const backendCatalog = readFileSync(backendCatalogPath, "utf8").replace(/\r\n/g, "\n");
  assert.equal(aiCatalog, backendCatalog);
});

test("hybrid resume extraction recovers skills missed by NER without false positives from CV prose", () => {
  const rawText = [
    "CURRICULUM VITAE",
    "Skills: C++, Node.js, FastAPI, PostgreSQL, CI/CD, Flutter",
    "I want to go to a great team and build scalable services with JavaScript."
  ].join("\n");

  const result = toAnalysisResult({
    personal: { name: "Test Student", email: null, phone: null, location: null },
    experience: [],
    education: [],
    skills: ["Docker"], // simulate NER only catching Docker
    certifications: [],
    seniority: "junior",
    country: null,
    experience_years: 0,
    _rawText: rawText
  }, "./models/resume-ner", rawText);

  assert.deepEqual(
    result.skills,
    ["Docker", "C++", "JavaScript", "Node.js", "Flutter", "FastAPI", "PostgreSQL", "CI/CD"]
  );
  assert.ok(!result.skills.includes("Computer Vision"), "lowercase 'cv' / 'CURRICULUM VITAE' must not trigger Computer Vision");
  assert.ok(!result.skills.includes("Go"), "lowercase 'go' in prose must not trigger Go");
  assert.ok(!result.skills.includes("Java"), "JavaScript must not trigger Java");
});

test("boundary-aware scan handles C, C++, C#, .NET, and avoids Java/JavaScript collision", () => {
  const scanned = scanSkillsInText("Tech Stack: C, C++, C#, .NET Core, JavaScript, Next.js, Tailwind CSS");
  assert.deepEqual(scanned, ["C", "C++", "C#", "JavaScript", "Next.js", "Tailwind CSS", ".NET"]);
  assert.ok(!scanned.includes("Java"));
});

test("section-aware JD analysis classifies bulleted required and preferred skills without repeating keywords", async () => {
  const description = [
    "Required Skills:",
    "- Python and FastAPI",
    "- PostgreSQL and Redis",
    "Preferred:",
    "- Docker and Kubernetes",
    "- Next.js"
  ].join("\n");

  const result = await analyzeOpportunity(
    "Backend & Platform Intern",
    description,
    "./models/jd-embedding",
    async (texts) => texts.map(() => [1, 0, 0, 0])
  );

  assert.deepEqual(
    result.requiredSkillSuggestions.map((s) => s.name),
    ["Python", "FastAPI", "PostgreSQL", "Redis"]
  );
  assert.deepEqual(
    result.preferredSkillSuggestions.map((s) => s.name),
    ["Docker", "Kubernetes", "Next.js"]
  );
});
