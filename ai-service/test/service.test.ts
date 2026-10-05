import assert from "node:assert/strict";
import { test } from "node:test";
import { buildApp } from "../src/server.js";
import { normalizeSkills } from "../src/analysis.js";
import { analyzeOpportunity } from "../src/jd-analysis.js";
import { matchOpportunities, matchOpportunity, type OpportunityMatchInput } from "../src/opportunity-matching.js";

const matchInput: OpportunityMatchInput = {
  student: { skills: [{ name: "JAVA", normalizedName: "java" }, { name: "MySQL", normalizedName: "mysql" }, { name: "JavaScript", normalizedName: "javascript" }], headline: "Backend developer", careerInterests: ["Cloud engineering"], experience: ["Built REST services"] },
  opportunity: { title: "Backend Intern", description: "Build backend systems", requiredSkills: [{ name: "Java", normalizedName: "java" }, { name: "PostgreSQL", normalizedName: "postgresql" }], preferredSkills: [{ name: "Kubernetes", normalizedName: "kubernetes" }], roleSignals: ["Backend Development"], responsibilitySignals: ["Develop REST services"] }
};

test("health endpoint reports service startup state", async () => {
  const app = buildApp({ modelPath: "/definitely/missing/model" });
  const response = await app.inject({ method: "GET", url: "/health" });
  assert.equal(response.statusCode, 200);
  assert.deepEqual(response.json(), { status: "UP", modelReady: false });
  await app.close();
});

test("multipart PDF returns structured JSON through analyzer path", async () => {
  let invoked = false;
  const app = buildApp({ analyzer: async () => {
    invoked = true;
    return { modelName: "test-model", modelVersion: "1", skills: ["Java"], experience: [], education: [], certifications: [] };
  }});
  const form = new FormData();
  form.set("file", new File(["%PDF-1.4"], "resume.pdf", { type: "application/pdf" }));
  const response = await app.inject({ method: "POST", url: "/internal/resume/analyze", payload: form });
  assert.equal(response.statusCode, 200);
  assert.equal(invoked, true);
  assert.deepEqual(response.json().skills, ["Java"]);
  await app.close();
});

test("empty and corrupt inputs are rejected safely", async () => {
  const app = buildApp({ analyzer: async () => { throw new Error("Could not extract text from PDF - file may be empty or unreadable"); } });
  const response = await app.inject({
    method: "POST", url: "/internal/resume/analyze",
    headers: { "content-type": "multipart/form-data; boundary=x" },
    payload: "--x\r\nContent-Disposition: form-data; name=\"file\"; filename=\"resume.pdf\"\r\nContent-Type: application/pdf\r\n\r\nbroken\r\n--x--\r\n"
  });
  assert.equal(response.statusCode, 422);
  assert.equal(response.json().code, "DOCUMENT_ANALYSIS_FAILED");
  await app.close();
});

test("skill cleanup is conservative and case-insensitive", () => {
  assert.deepEqual(normalizeSkills([" java ", "Java", "POSTGRESQL", "HTML/CSS", "HTML", "CSS", "Domain Skill"]),
    ["Java", "PostgreSQL", "HTML/CSS", "Domain Skill"]);
});

test("opportunity endpoint returns structured hybrid NLP results", async () => {
  let invoked = false;
  const app = buildApp({ opportunityAnalyzer: async () => {
    invoked = true;
    return {
      modelName: "semantic-test-model", modelVersion: "1",
      technicalSkills: [{ name: "Java", normalizedName: "java" }],
      requiredSkillSuggestions: [{ name: "Java", normalizedName: "java" }], preferredSkillSuggestions: [],
      roleSignals: ["Backend Development"], responsibilitySignals: ["Build scalable services"]
    };
  }});
  const response = await app.inject({ method: "POST", url: "/internal/opportunities/analyze",
    payload: { title: "Backend Intern", description: "Java is required. Build scalable services." } });
  assert.equal(response.statusCode, 200); assert.equal(invoked, true);
  assert.equal(response.json().requiredSkillSuggestions[0].name, "Java");
  await app.close();
});

test("hybrid parser normalizes duplicates and preserves required and preferred context", async () => {
  const dimensions = 3;
  const embed = async (texts: string[]) => texts.map((_, index) => index === 0 ? [1, 0, 0]
    : index <= 7 ? (index === 1 ? [0.9, 0.1, 0] : [0, 1, 0])
    : [0.8, 0.2, 0].slice(0, dimensions));
  const result = await analyzeOpportunity("Backend Intern",
    "JAVA and Java with Spring Boot are required. Docker, AWS or Kubernetes are preferred. The intern will build scalable backend services.",
    "/missing", embed);
  assert.deepEqual(result.requiredSkillSuggestions.map(value => value.name), ["Java", "Spring Boot"]);
  assert.deepEqual(result.preferredSkillSuggestions.map(value => value.name), ["Docker", "Kubernetes", "AWS"]);
  assert.equal(new Set(result.technicalSkills.map(value => value.normalizedName)).size, result.technicalSkills.length);
  assert.ok(result.roleSignals.includes("Backend Development"));
  assert.ok(result.responsibilitySignals.some(value => /build scalable backend services/i.test(value)));
});

test("empty and malformed opportunity requests are rejected safely", async () => {
  const app = buildApp();
  const empty = await app.inject({ method: "POST", url: "/internal/opportunities/analyze", payload: { title: "Intern", description: " " } });
  assert.equal(empty.statusCode, 422); assert.equal(empty.json().code, "EMPTY_JOB_DESCRIPTION");
  const malformed = await app.inject({ method: "POST", url: "/internal/opportunities/analyze", payload: { title: 4, description: [] } });
  assert.equal(malformed.statusCode, 400); assert.equal(malformed.json().code, "INVALID_REQUEST");
  await app.close();
});

test("matching uses exact normalized matches before conservative one-to-one semantic matches", async () => {
  let calls = 0;
  const embed = async (texts: string[]) => { calls++; return texts.map(text => {
    if (["MySQL", "PostgreSQL"].includes(text)) return [0.99, 0.1, 0];
    if (["Backend developer", "Backend Development", "Backend Intern"].includes(text)) return [0, 0.99, 0.1];
    if (["Built REST services", "Develop REST services"].includes(text)) return [0.1, 0, 0.99];
    return [0.2, 0.2, 0.2];
  }); };
  const result = await matchOpportunity(matchInput, "/missing", embed);
  assert.equal(calls, 1);
  assert.deepEqual(result.requiredSkillMatches.map(value => value.matchType), ["EXACT", "RELATED"]);
  assert.equal(result.requiredSkillMatches[1].studentSkill, "MySQL");
  assert.equal(result.preferredSkillMatches[0].matchType, "MISSING");
  assert.equal(result.roleEvidence.length, 1); assert.equal(result.experienceEvidence.length, 1);
});

test("matching prevents misleading Java to JavaScript semantic evidence", async () => {
  const input: OpportunityMatchInput = { ...matchInput,
    student: { skills: [{ name: "JavaScript", normalizedName: "javascript" }], headline: "", careerInterests: [], experience: [] },
    opportunity: { ...matchInput.opportunity, requiredSkills: [{ name: "Java", normalizedName: "java" }], preferredSkills: [], roleSignals: [], responsibilitySignals: [] }
  };
  const result = await matchOpportunity(input, "/missing", async texts => texts.map(() => [1, 0, 0]));
  assert.equal(result.requiredSkillMatches[0].matchType, "MISSING");
});

test("empty skill sets and unrelated skills remain honest", async () => {
  const noStudentSkills = await matchOpportunity({ ...matchInput, student: { ...matchInput.student, skills: [] } }, "/missing", async texts => texts.map(() => [1, 0, 0]));
  assert.ok(noStudentSkills.requiredSkillMatches.every(value => value.matchType === "MISSING"));
  const noOpportunitySkills = await matchOpportunity({ ...matchInput, opportunity: { ...matchInput.opportunity, requiredSkills: [], preferredSkills: [] } }, "/missing", async texts => texts.map(() => [1, 0, 0]));
  assert.deepEqual(noOpportunitySkills.requiredSkillMatches, []); assert.deepEqual(noOpportunitySkills.preferredSkillMatches, []);
  const unrelated = await matchOpportunity({ ...matchInput,
    student: { ...matchInput.student, skills: [{ name: "Graphic Design", normalizedName: "graphic design" }] },
    opportunity: { ...matchInput.opportunity, requiredSkills: [{ name: "Kubernetes", normalizedName: "kubernetes" }], preferredSkills: [] }
  }, "/missing", async texts => texts.map((_, index) => index === 0 ? [1, 0, 0] : [0, 1, 0]));
  assert.equal(unrelated.requiredSkillMatches[0].matchType, "MISSING");
});

test("one student skill satisfies at most one related requirement per group", async () => {
  const input: OpportunityMatchInput = { ...matchInput,
    student: { ...matchInput.student, skills: [{ name: "Cloud Platform", normalizedName: "cloud platform" }] },
    opportunity: { ...matchInput.opportunity, requiredSkills: [{ name: "AWS", normalizedName: "aws" }, { name: "Azure", normalizedName: "azure" }], preferredSkills: [] }
  };
  const result = await matchOpportunity(input, "/missing", async texts => texts.map(text => [text === "Cloud Platform" ? 1 : 0.99, 0.1, 0]));
  assert.equal(result.requiredSkillMatches.filter(value => value.matchType === "RELATED").length, 1);
  assert.equal(result.requiredSkillMatches.filter(value => value.matchType === "MISSING").length, 1);
});

test("matching endpoint rejects malformed input and returns structured evidence", async () => {
  const app = buildApp({ opportunityMatcher: async () => ({ modelName: "test", modelVersion: "1", relatedSkillThreshold: 0.72,
    requiredSkillMatches: [{ opportunitySkill: "Java", matchType: "EXACT", studentSkill: "JAVA", semanticSimilarity: 1 }],
    preferredSkillMatches: [], roleEvidence: [], experienceEvidence: [] }) });
  const malformed = await app.inject({ method: "POST", url: "/internal/opportunities/match", payload: { student: {} } });
  assert.equal(malformed.statusCode, 400);
  const valid = await app.inject({ method: "POST", url: "/internal/opportunities/match", payload: matchInput });
  assert.equal(valid.statusCode, 200); assert.equal(valid.json().requiredSkillMatches[0].matchType, "EXACT");
  await app.close();
});

test("batch matching embeds all candidates in one inference and preserves canonical evidence", async () => {
  let calls = 0;
  const results = await matchOpportunities([matchInput, { ...matchInput, student: { ...matchInput.student, headline: "Cloud engineer" } }],
    "/missing", async texts => { calls++; return texts.map(text => text === "MySQL" || text === "PostgreSQL" ? [1, 0, 0] : [0, 1, 0]); });
  assert.equal(calls, 1);
  assert.equal(results.length, 2);
  assert.equal(results[0].requiredSkillMatches[0].matchType, "EXACT");
  assert.equal(results[1].requiredSkillMatches[0].matchType, "EXACT");
});

test("batch matching endpoint validates its boundary and returns one result per candidate", async () => {
  let received = 0;
  const app = buildApp({ opportunityBatchMatcher: async inputs => {
    received = inputs.length;
    return inputs.map(() => ({ modelName: "test", modelVersion: "1", relatedSkillThreshold: 0.72,
      requiredSkillMatches: [], preferredSkillMatches: [], roleEvidence: [], experienceEvidence: [] }));
  }});
  const malformed = await app.inject({ method: "POST", url: "/internal/opportunities/match-batch", payload: { matches: [{ student: {} }] } });
  assert.equal(malformed.statusCode, 400);
  const valid = await app.inject({ method: "POST", url: "/internal/opportunities/match-batch", payload: { matches: [matchInput, matchInput] } });
  assert.equal(valid.statusCode, 200);
  assert.equal(received, 2);
  assert.equal(valid.json().matches.length, 2);
  await app.close();
});
