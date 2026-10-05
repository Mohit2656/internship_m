import { localEmbedder, semanticModelVersion, type SemanticEmbedder } from "./jd-analysis.js";

export const RELATED_SKILL_THRESHOLD = Number(process.env.RELATED_SKILL_THRESHOLD || "0.72");
const EVIDENCE_THRESHOLD = Number(process.env.MATCH_EVIDENCE_THRESHOLD || "0.52");
const modelName = "onnx-community/all-MiniLM-L6-v2-ONNX";

export type MatchSkill = { name: string; normalizedName: string };
export type OpportunityMatchInput = {
  student: { skills: MatchSkill[]; headline: string; careerInterests: string[]; experience: string[] };
  opportunity: { title: string; description: string; requiredSkills: MatchSkill[]; preferredSkills: MatchSkill[]; roleSignals: string[]; responsibilitySignals: string[] };
};
export type SkillMatch = { opportunitySkill: string; matchType: "EXACT" | "RELATED" | "MISSING"; studentSkill: string | null; semanticSimilarity: number | null };
export type EvidenceMatch = { studentSignal: string; opportunitySignal: string; semanticSimilarity: number };
export type OpportunityMatchResult = {
  modelName: string; modelVersion: string; relatedSkillThreshold: number;
  requiredSkillMatches: SkillMatch[]; preferredSkillMatches: SkillMatch[];
  roleEvidence: EvidenceMatch[]; experienceEvidence: EvidenceMatch[];
};

function key(value: string) { return value.trim().toLocaleLowerCase("en-US").replace(/\s+/g, " "); }
function cosine(a: number[], b: number[]) { return a.reduce((sum, value, index) => sum + value * b[index], 0); }
function score(value: number) { return Math.round(value * 1_000_000) / 1_000_000; }
function unsafeAlias(a: string, b: string) {
  const pair = new Set([key(a), key(b)]);
  return (pair.has("java") && pair.has("javascript")) ||
    (pair.has("c") && (pair.has("c++") || pair.has("c#")));
}

function exactFirst(opportunitySkills: MatchSkill[], studentSkills: MatchSkill[]) {
  const unused = new Set(studentSkills.map((_, index) => index));
  const results = new Map<number, SkillMatch>();
  opportunitySkills.forEach((opportunitySkill, opportunityIndex) => {
    const found = [...unused].find(index => key(studentSkills[index].normalizedName) === key(opportunitySkill.normalizedName));
    if (found !== undefined) {
      unused.delete(found);
      results.set(opportunityIndex, { opportunitySkill: opportunitySkill.name, matchType: "EXACT", studentSkill: studentSkills[found].name, semanticSimilarity: 1 });
    }
  });
  return { unused, results };
}

function semanticSkills(opportunitySkills: MatchSkill[], studentSkills: MatchSkill[], vectors: Map<string, number[]>) {
  const { unused, results } = exactFirst(opportunitySkills, studentSkills);
  const pairs: { opportunityIndex: number; studentIndex: number; similarity: number }[] = [];
  opportunitySkills.forEach((opportunitySkill, opportunityIndex) => {
    if (results.has(opportunityIndex)) return;
    unused.forEach(studentIndex => {
      const studentSkill = studentSkills[studentIndex];
      if (unsafeAlias(opportunitySkill.normalizedName, studentSkill.normalizedName)) return;
      pairs.push({ opportunityIndex, studentIndex, similarity: cosine(vectors.get(opportunitySkill.name)!, vectors.get(studentSkill.name)!) });
    });
  });
  pairs.sort((a, b) => b.similarity - a.similarity);
  const assignedOpportunities = new Set<number>(); const assignedStudents = new Set<number>();
  for (const pair of pairs) {
    if (pair.similarity < RELATED_SKILL_THRESHOLD || assignedOpportunities.has(pair.opportunityIndex) || assignedStudents.has(pair.studentIndex)) continue;
    assignedOpportunities.add(pair.opportunityIndex); assignedStudents.add(pair.studentIndex);
    results.set(pair.opportunityIndex, { opportunitySkill: opportunitySkills[pair.opportunityIndex].name, matchType: "RELATED", studentSkill: studentSkills[pair.studentIndex].name, semanticSimilarity: score(pair.similarity) });
  }
  return opportunitySkills.map((skill, index) => results.get(index) || { opportunitySkill: skill.name, matchType: "MISSING" as const, studentSkill: null, semanticSimilarity: null });
}

function semanticEvidence(studentSignals: string[], opportunitySignals: string[], vectors: Map<string, number[]>) {
  const pairs: (EvidenceMatch & { studentIndex: number; opportunityIndex: number })[] = [];
  studentSignals.forEach((studentSignal, studentIndex) => opportunitySignals.forEach((opportunitySignal, opportunityIndex) => {
    pairs.push({ studentSignal, opportunitySignal, studentIndex, opportunityIndex, semanticSimilarity: cosine(vectors.get(studentSignal)!, vectors.get(opportunitySignal)!) });
  }));
  pairs.sort((a, b) => b.semanticSimilarity - a.semanticSimilarity);
  const students = new Set<number>(); const opportunities = new Set<number>(); const selected: EvidenceMatch[] = [];
  for (const pair of pairs) {
    if (pair.semanticSimilarity < EVIDENCE_THRESHOLD || students.has(pair.studentIndex) || opportunities.has(pair.opportunityIndex)) continue;
    students.add(pair.studentIndex); opportunities.add(pair.opportunityIndex);
    selected.push({ studentSignal: pair.studentSignal, opportunitySignal: pair.opportunitySignal, semanticSimilarity: score(pair.semanticSimilarity) });
  }
  return selected.slice(0, 5);
}

export async function matchOpportunity(input: OpportunityMatchInput, modelPath: string, injected?: SemanticEmbedder): Promise<OpportunityMatchResult> {
  const opportunityContext = `${input.opportunity.title}. ${input.opportunity.description}`.trim();
  const allTexts = [...input.student.skills.map(x => x.name), ...input.opportunity.requiredSkills.map(x => x.name),
    ...input.opportunity.preferredSkills.map(x => x.name), input.student.headline, ...input.student.careerInterests,
    ...input.student.experience, opportunityContext, ...input.opportunity.roleSignals, ...input.opportunity.responsibilitySignals]
    .map(value => value.trim()).filter(Boolean);
  const uniqueTexts = [...new Set(allTexts)];
  const vectors = new Map<string, number[]>();
  if (uniqueTexts.length) {
    const embed = injected || await localEmbedder(modelPath);
    const embedded = await embed(uniqueTexts);
    uniqueTexts.forEach((text, index) => vectors.set(text, embedded[index]));
  }
  const studentRoleSignals = [input.student.headline, ...input.student.careerInterests].map(x => x.trim()).filter(Boolean);
  const opportunityRoleSignals = [...input.opportunity.roleSignals, opportunityContext].map(x => x.trim()).filter(Boolean);
  const roleEvidence = semanticEvidence(studentRoleSignals, opportunityRoleSignals, vectors).map(value =>
    value.opportunitySignal === opportunityContext ? { ...value, opportunitySignal: input.opportunity.title } : value);
  return {
    modelName, modelVersion: semanticModelVersion(modelPath), relatedSkillThreshold: RELATED_SKILL_THRESHOLD,
    requiredSkillMatches: semanticSkills(input.opportunity.requiredSkills, input.student.skills, vectors),
    preferredSkillMatches: semanticSkills(input.opportunity.preferredSkills, input.student.skills, vectors),
    roleEvidence,
    experienceEvidence: semanticEvidence(input.student.experience, input.opportunity.responsibilitySignals, vectors)
  };
}

export async function matchOpportunities(inputs: OpportunityMatchInput[], modelPath: string, injected?: SemanticEmbedder): Promise<OpportunityMatchResult[]> {
  if (!inputs.length) return [];
  const embed = injected || await localEmbedder(modelPath);
  const texts = [...new Set(inputs.flatMap(input => [
    ...input.student.skills.map(value => value.name), ...input.opportunity.requiredSkills.map(value => value.name),
    ...input.opportunity.preferredSkills.map(value => value.name), input.student.headline, ...input.student.careerInterests,
    ...input.student.experience, `${input.opportunity.title}. ${input.opportunity.description}`.trim(),
    ...input.opportunity.roleSignals, ...input.opportunity.responsibilitySignals
  ]).map(value => value.trim()).filter(Boolean))];
  const embedded = texts.length ? await embed(texts) : [];
  const cache = new Map(texts.map((text, index) => [text, embedded[index]]));
  const cached: SemanticEmbedder = async values => values.map(value => cache.get(value)!);
  return Promise.all(inputs.map(input => matchOpportunity(input, modelPath, cached)));
}
