import type { StudentProfile } from "@/lib/student-profile";
import type { ProfessionalProfile } from "@/lib/professional-profile";
import type { InternshipOnboarding } from "@/lib/internship-onboarding";
import type { InternshipDetails } from "@/lib/internship-details";

export type ProfileCompletionSection = {
  id: "personal" | "professional" | "internship";
  title: string;
  weight: number;
  completedCount: number;
  totalCount: number;
  percentage: number;
  missingFields: string[];
  actionLabel: string;
  actionHref: string;
  applicable: boolean;
};

export type ProfileCompletionResult = {
  percentage: number;
  remainingPercentage: number;
  isComplete: boolean;
  sections: ProfileCompletionSection[];
  incompleteSections: ProfileCompletionSection[];
  nextAction: { label: string; href: string } | null;
};

function hasValue(val: unknown): boolean {
  if (val === null || val === undefined) return false;
  if (typeof val === "string") return val.trim().length > 0;
  if (typeof val === "number") return !Number.isNaN(val);
  return true;
}

export function calculateProfileCompletion(params: {
  profile: StudentProfile | null;
  professionalProfile?: ProfessionalProfile | null;
  skillsCount?: number;
  onboarding?: InternshipOnboarding | null;
  details?: InternshipDetails | null;
}): ProfileCompletionResult {
  const { profile, professionalProfile, skillsCount = 0, onboarding, details } = params;

  // 1. Personal and academic details, including the student's assigned section.
  const personalChecks: { label: string; done: boolean }[] = [
    { label: "Personal Email", done: hasValue(profile?.personalEmail) },
    { label: "Phone Number", done: hasValue(profile?.phoneNumber) },
    { label: "Blood Group", done: hasValue(profile?.bloodGroup) },
    { label: "Roll Number", done: hasValue(profile?.rollNumber) },
    { label: "Academic Year", done: hasValue(profile?.academicYear) },
    { label: "Semester", done: profile?.semester === 8 },
    { label: "Section A or B", done: profile?.section === "A" || profile?.section === "B" },
    { label: "Permanent Address", done: hasValue(profile?.permanentAddress) },
    { label: "Parent / Guardian Name", done: hasValue(profile?.guardianName) },
    { label: "Parent / Guardian Contact", done: hasValue(profile?.guardianContact) },
    { label: "Emergency Contact", done: hasValue(profile?.emergencyContact) },
    { label: "Aadhaar Identity", done: hasValue(profile?.maskedAadhaar) },
  ];
  const personalDone = personalChecks.filter((c) => c.done).length;
  const personalTotal = personalChecks.length;
  const personalRatio = personalTotal > 0 ? personalDone / personalTotal : 0;
  const personalMissing = personalChecks.filter((c) => !c.done).map((c) => c.label);

  // 2. Professional Profile Fields (4 core items)
  const hasSkills = skillsCount > 0 || hasValue(professionalProfile?.skillsUpdatedAt);
  const professionalChecks: { label: string; done: boolean }[] = [
    { label: "Professional Headline", done: hasValue(professionalProfile?.headline) },
    { label: "Career Interests", done: hasValue(professionalProfile?.careerInterests) },
    { label: "Uploaded Resume", done: Boolean(professionalProfile?.resume) },
    { label: "Confirmed Skills", done: hasSkills },
  ];
  const professionalDone = professionalChecks.filter((c) => c.done).length;
  const professionalTotal = professionalChecks.length;
  const professionalRatio = professionalTotal > 0 ? professionalDone / professionalTotal : 0;
  const professionalMissing = professionalChecks.filter((c) => !c.done).map((c) => c.label);

  // 3. Internship Details (Applicable only if student has registered an internship / started onboarding)
  const nonApplicableStatuses = ["PRE_COMMENCEMENT", "NOT_STARTED"];
  const isInternshipApplicable = Boolean(
    details ||
      (onboarding?.workflowStatus && !nonApplicableStatuses.includes(onboarding.workflowStatus))
  );

  let internshipRatio = 0;
  let internshipDone = 0;
  let internshipTotal = 7;
  let internshipMissing: string[] = [];

  if (isInternshipApplicable) {
    const internshipChecks: { label: string; done: boolean }[] = [
      { label: "Company Name", done: hasValue(details?.companyName) },
      { label: "Company Address", done: hasValue(details?.companyAddress) },
      { label: "Supervisor Name", done: hasValue(details?.industrySupervisorName) },
      { label: "Supervisor Contact", done: hasValue(details?.industrySupervisorContact) },
      { label: "Internship Mode", done: hasValue(details?.internshipMode) },
      { label: "Start Date", done: hasValue(details?.startDate) },
      { label: "End Date", done: hasValue(details?.endDate) },
    ];
    internshipDone = internshipChecks.filter((c) => c.done).length;
    internshipTotal = internshipChecks.length;
    internshipRatio = internshipTotal > 0 ? internshipDone / internshipTotal : 0;
    internshipMissing = internshipChecks.filter((c) => !c.done).map((c) => c.label);
  }

  // Weighting calculation
  let percentage: number;
  if (isInternshipApplicable) {
    percentage = Math.round(
      personalRatio * 40 + professionalRatio * 30 + internshipRatio * 30
    );
  } else {
    percentage = Math.round(personalRatio * 60 + professionalRatio * 40);
  }

  // Ensure bounds
  if (personalDone === personalTotal && professionalDone === professionalTotal && (!isInternshipApplicable || internshipDone === internshipTotal)) {
    percentage = 100;
  }
  percentage = Math.max(0, Math.min(100, percentage));
  const remainingPercentage = 100 - percentage;
  const isComplete = percentage === 100;

  const personalSection: ProfileCompletionSection = {
    id: "personal",
    title: "Personal Profile",
    weight: isInternshipApplicable ? 40 : 60,
    completedCount: personalDone,
    totalCount: personalTotal,
    percentage: Math.round(personalRatio * 100),
    missingFields: personalMissing,
    actionLabel: "Complete Personal Profile",
    actionHref: "/student/settings?tab=profile&edit=true",
    applicable: true,
  };

  const professionalSection: ProfileCompletionSection = {
    id: "professional",
    title: "Professional Profile & Skills",
    weight: isInternshipApplicable ? 30 : 40,
    completedCount: professionalDone,
    totalCount: professionalTotal,
    percentage: Math.round(professionalRatio * 100),
    missingFields: professionalMissing,
    actionLabel: "Update Professional Profile",
    actionHref: "/student/professional-profile",
    applicable: true,
  };

  const internshipSection: ProfileCompletionSection = {
    id: "internship",
    title: "Internship Details",
    weight: isInternshipApplicable ? 30 : 0,
    completedCount: internshipDone,
    totalCount: internshipTotal,
    percentage: isInternshipApplicable ? Math.round(internshipRatio * 100) : 100,
    missingFields: internshipMissing,
    actionLabel: "Complete Internship Details",
    actionHref: "/student/internship/details",
    applicable: isInternshipApplicable,
  };

  const sections = [personalSection, professionalSection, internshipSection];
  const incompleteSections = sections.filter(
    (s) => s.applicable && s.completedCount < s.totalCount
  );

  let nextAction: { label: string; href: string } | null = null;
  if (incompleteSections.length > 0) {
    const first = incompleteSections[0];
    nextAction = { label: first.actionLabel, href: first.actionHref };
  }

  return {
    percentage,
    remainingPercentage,
    isComplete,
    sections,
    incompleteSections,
    nextAction,
  };
}
