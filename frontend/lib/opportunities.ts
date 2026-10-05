export type OpportunityStatus = "DRAFT" | "PUBLISHED" | "CLOSED";
export type OpportunityMode = "ONSITE" | "HYBRID" | "REMOTE";
export type Branch = "COMPUTER_ENGINEERING" | "INFORMATION_TECHNOLOGY" | "ELECTRONICS" | "ELECTRICAL" | "MECHANICAL" | "CIVIL";

export const branchLabels: Record<Branch, string> = {
  COMPUTER_ENGINEERING: "Computer Engineering",
  INFORMATION_TECHNOLOGY: "Information Technology",
  ELECTRONICS: "Electronics",
  ELECTRICAL: "Electrical",
  MECHANICAL: "Mechanical",
  CIVIL: "Civil",
};

export type OpportunitySkill = { id: number; displayName: string; normalizedName: string };
export type OpportunityAiReviewStatus = "PENDING" | "CONFIRMED" | "REJECTED";
export type OpportunityAiStatus = "NOT_ANALYZED" | "PENDING" | "ANALYZED" | "FAILED" | "STALE";
export type OpportunityAiSuggestion = {
  id: number;
  displayName: string;
  normalizedName: string;
  reviewStatus: OpportunityAiReviewStatus;
};
export type OpportunityAiAnalysis = {
  analysisId: number | null;
  opportunityId: number;
  status: OpportunityAiStatus;
  stale: boolean;
  modelName: string | null;
  modelVersion: string | null;
  safeFailureReason: string | null;
  startedAt: string | null;
  completedAt: string | null;
  requiredSuggestions: OpportunityAiSuggestion[];
  preferredSuggestions: OpportunityAiSuggestion[];
  roleSignals: string[];
  responsibilitySignals: string[];
};

export type Opportunity = {
  id: number;
  companyName: string;
  title: string;
  description: string | null;
  companyWebsite: string | null;
  location: string | null;
  internshipMode: OpportunityMode | null;
  durationMonths: number | null;
  stipendAmount: number;
  currency: string;
  applicationDeadline: string | null;
  minimumCgpa: number | null;
  status: OpportunityStatus;
  createdBy: number;
  publishedAt: string | null;
  closedAt: string | null;
  createdAt: string;
  updatedAt: string;
  eligibleBranches: Branch[];
  eligibleSemesters: number[];
  requiredSkills: OpportunitySkill[];
  preferredSkills: OpportunitySkill[];
};

export type OpportunityInput = {
  companyName: string;
  title: string;
  description: string;
  companyWebsite: string;
  location: string;
  internshipMode: OpportunityMode | "";
  durationMonths: string;
  stipendAmount: string;
  currency: string;
  applicationDeadline: string;
  minimumCgpa: string;
  eligibleBranches: Branch[];
  eligibleSemesters: number[];
  requiredSkills: string[];
  preferredSkills: string[];
};

export type StudentOpportunitySummary = {
  id: number;
  companyName: string;
  title: string;
  mode: OpportunityMode;
  location: string | null;
  stipendAmount: number;
  currency: string;
  applicationDeadline: string | null;
  publishedAt: string;
  requiredSkills: string[];
};

export type StudentOpportunityDetail = {
  id: number;
  companyName: string;
  title: string;
  description: string;
  companyWebsite: string | null;
  mode: OpportunityMode;
  location: string | null;
  durationMonths: number | null;
  stipendAmount: number;
  currency: string;
  applicationDeadline: string | null;
  minimumCgpa: number | null;
  eligibleBranches: Branch[];
  eligibleSemesters: number[];
  requiredSkills: string[];
  preferredSkills: string[];
};

export type EligibilityStatus = "PASS" | "FAIL" | "UNKNOWN" | "NOT_APPLICABLE";
export type OpportunitySkillMatch = {
  opportunitySkill: string;
  matchType: "EXACT" | "RELATED" | "MISSING";
  studentSkill: string | null;
  semanticSimilarity: number | null;
};
export type OpportunityMatch = {
  opportunityId: number;
  eligibility: {
    overall: "ELIGIBLE" | "INELIGIBLE" | "UNKNOWN";
    criteria: { type: "BRANCH" | "SEMESTER" | "CGPA"; status: EligibilityStatus; requirement: string; studentValue: string | null; message: string }[];
  };
  semanticStatus: "AVAILABLE" | "UNAVAILABLE";
  semanticMessage: string | null;
  profileNotice: string | null;
  evidence: null | {
    modelName: string;
    modelVersion: string;
    relatedSkillThreshold: number;
    requiredSkills: OpportunitySkillMatch[];
    preferredSkills: OpportunitySkillMatch[];
    roleEvidence: { studentSignal: string; opportunitySignal: string; semanticSimilarity: number }[];
    experienceEvidence: { studentSignal: string; opportunitySignal: string; semanticSimilarity: number }[];
  };
  explanation: {
    summary: string;
    alignedSkills: SkillExplanation[];
    skillsToStrengthen: SkillExplanation[];
    additionalPreferredSkills: SkillExplanation[];
  };
};

export type SkillExplanation = {
  skill: string;
  matchType: "EXACT" | "RELATED" | "MISSING";
  studentSkill: string | null;
  detail: string;
};

export type StudentOpportunityPage = {
  content: StudentOpportunitySummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

export type TalentEligibilityFilter = "ELIGIBLE_OR_UNKNOWN" | "ELIGIBLE" | "UNKNOWN" | "INELIGIBLE" | "ALL";
export type TalentCandidate = {
  studentId: number;
  studentName: string;
  uid: string;
  branch: Branch;
  semester: number;
  exactRequiredSkills: number;
  relatedRequiredSkills: number;
  exactPreferredSkills: number;
  alignment: OpportunityMatch;
};
export type TalentDiscoveryPage = {
  opportunity: { id: number; companyName: string; title: string; status: OpportunityStatus };
  orderingExplanation: string;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  candidates: TalentCandidate[];
};
export type StudentSkillGapAnalysis = {
  status: "AVAILABLE" | "INCOMPLETE_PROFILE" | "UNAVAILABLE";
  message: string | null;
  opportunitiesAnalyzed: number;
  skillsToStrengthen: { skill: string; requiredByCount: number; preferredByCount: number }[];
  existingStrengths: { skill: string; exactMatchCount: number }[];
  relatedSkills: { studentSkill: string; opportunitySkill: string; opportunityCount: number }[];
};
export type AdminSkillGapAnalysis = {
  status: "AVAILABLE" | "UNAVAILABLE";
  message: string | null;
  candidatesAnalyzed: number;
  requiredGaps: { skill: string; studentsMissing: number }[];
  preferredGaps: { skill: string; studentsMissing: number }[];
};

export function formatOpportunityDate(value: string | null) {
  return value ? new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" }).format(new Date(`${value}T00:00:00`)) : "No deadline";
}

export function formatOpportunityStipend(amount: number, currency: string) {
  if (Number(amount) === 0) return "Unpaid";
  try { return `${new Intl.NumberFormat("en-IN", { style: "currency", currency, maximumFractionDigits: 2 }).format(amount)} / month`; }
  catch { return `${amount} ${currency} / month`; }
}

export function safeCompanyUrl(value: string | null) {
  if (!value) return null;
  try { const url = new URL(value); return url.protocol === "http:" || url.protocol === "https:" ? value : null; }
  catch { return null; }
}

export const emptyOpportunity: OpportunityInput = {
  companyName: "", title: "", description: "", companyWebsite: "", location: "", internshipMode: "",
  durationMonths: "", stipendAmount: "0", currency: "INR", applicationDeadline: "", minimumCgpa: "",
  eligibleBranches: [], eligibleSemesters: [], requiredSkills: [], preferredSkills: [],
};

export function toInput(opportunity: Opportunity): OpportunityInput {
  return {
    companyName: opportunity.companyName,
    title: opportunity.title,
    description: opportunity.description ?? "",
    companyWebsite: opportunity.companyWebsite ?? "",
    location: opportunity.location ?? "",
    internshipMode: opportunity.internshipMode ?? "",
    durationMonths: opportunity.durationMonths?.toString() ?? "",
    stipendAmount: opportunity.stipendAmount.toString(),
    currency: opportunity.currency,
    applicationDeadline: opportunity.applicationDeadline ?? "",
    minimumCgpa: opportunity.minimumCgpa?.toString() ?? "",
    eligibleBranches: opportunity.eligibleBranches,
    eligibleSemesters: opportunity.eligibleSemesters,
    requiredSkills: opportunity.requiredSkills.map(skill => skill.displayName),
    preferredSkills: opportunity.preferredSkills.map(skill => skill.displayName),
  };
}

export function toPayload(input: OpportunityInput) {
  return {
    ...input,
    internshipMode: input.internshipMode || null,
    durationMonths: input.durationMonths ? Number(input.durationMonths) : null,
    stipendAmount: Number(input.stipendAmount),
    applicationDeadline: input.applicationDeadline || null,
    minimumCgpa: input.minimumCgpa ? Number(input.minimumCgpa) : null,
  };
}
