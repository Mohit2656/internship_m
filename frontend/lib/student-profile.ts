import { InternshipOnboarding, InternshipSource, WorkflowStatus } from "@/lib/internship-onboarding";
import { InternshipDetails } from "@/lib/internship-details";

export type ProfileStatus = "INCOMPLETE" | "COMPLETE";

export type StudentProfile = {
  id: number; studentName: string; branch: string; branchLabel: string; uid: string; rollNumber: string;
  permanentAddress: string; currentAddress: string | null; personalEmail: string; instituteEmail: string; phoneNumber: string;
  guardianName: string; guardianContact: string; emergencyContact: string; bloodGroup: string; maskedAadhaar: string;
  facultyMentorName: string; facultyMentorContact: string; internshipCoordinatorName: string; internshipCoordinatorContact: string;
  cdcCoordinatorName: string; cdcCoordinatorContact: string; academicYear: string; semester: number; currentCgpa: number | null; profileStatus: ProfileStatus;
  createdAt: string; updatedAt: string; section?: string | null; sectionNeedsReview?: boolean; academicDetailsNeedsReview?: boolean;
};

export type ProfilePrefill = {
  studentName: string | null; uid: string | null; instituteEmail: string; branch: string; branchLabel: string;
  semester: number | null; section?: string | null; readOnlyFields: Array<keyof ProfileFormData>;
};

export type ProfileFormData = Omit<StudentProfile, "id" | "branchLabel" | "maskedAadhaar" | "profileStatus" | "createdAt" | "updatedAt" | "semester" | "currentAddress" | "currentCgpa" | "section" | "sectionNeedsReview" | "academicDetailsNeedsReview"> & {
  section: string; currentAddress: string; semester: string; currentCgpa: string; aadhaarNumber: string;
};

export const emptyProfile: ProfileFormData = {
  studentName: "", branch: "COMPUTER_ENGINEERING", section: "", uid: "", rollNumber: "", permanentAddress: "", currentAddress: "", personalEmail: "",
  instituteEmail: "", phoneNumber: "", guardianName: "", guardianContact: "", emergencyContact: "", bloodGroup: "",
  aadhaarNumber: "", facultyMentorName: "", facultyMentorContact: "", internshipCoordinatorName: "",
  internshipCoordinatorContact: "", cdcCoordinatorName: "", cdcCoordinatorContact: "", academicYear: "", semester: "8", currentCgpa: ""
};

export function initialProfileForm(existing?: StudentProfile, institutional?: ProfilePrefill): ProfileFormData {
  if (existing) return { ...emptyProfile, ...existing, section: existing.section === "A" || existing.section === "B" ? existing.section : "", branch: "COMPUTER_ENGINEERING", currentAddress: existing.currentAddress ?? "", semester: "8", currentCgpa: existing.currentCgpa == null ? "" : String(existing.currentCgpa), aadhaarNumber: "" };
  return { ...emptyProfile, studentName: institutional?.studentName ?? "", branch: institutional?.branch ?? "COMPUTER_ENGINEERING",
    uid: institutional?.uid ?? "", instituteEmail: institutional?.instituteEmail ?? "",
    section: institutional?.section === "A" || institutional?.section === "B" ? institutional.section : "", semester: "8" };
}

export function profileReadOnlyFields(existing?: StudentProfile, institutional?: ProfilePrefill): Set<keyof ProfileFormData> {
  const fields = new Set<keyof ProfileFormData>(institutional?.readOnlyFields ?? ["branch", "instituteEmail"]);
  fields.add("branch"); fields.add("semester");
  if (existing) fields.add("uid");
  return fields;
}

export type StudentSummary = {
  id: number; loginEmail: string; studentName: string | null; uid: string | null; rollNumber: string | null; branch: string | null;
  branchLabel: string | null; instituteEmail: string | null; phoneNumber: string | null; academicYear: string | null;
  section?: string | null; sectionNeedsReview?: boolean; semester: number | null; profileStatus: ProfileStatus; internshipStatus: WorkflowStatus; internshipSource: InternshipSource | null;
  internshipSourceLabel: string | null; documentsCompleted: number; documentsRequired: number; diaryIssued: boolean;
  profileLastStudentUpdatedAt: string | null; onboardingLastStudentUpdatedAt: string | null;
  resumeUploadedAt: string | null; internshipDetailsUpdatedAt: string | null; workplaceUpdatedAt: string | null;
};
export type StudentDetail = { id: number; loginEmail: string; profileStatus: ProfileStatus; profile: StudentProfile | null; aadhaarNumber: string | null; internshipOnboarding: InternshipOnboarding; internshipDetails: InternshipDetails | null };
export type MentorStudentProfile = {
  studentName: string; uid: string; branch: string; branchLabel: string; rollNumber: string;
  instituteEmail: string; personalEmail: string; phoneNumber: string; academicYear: string; semester: number;
  section?: string | null; sectionNeedsReview?: boolean; professionalHeadline: string | null; careerInterests: string | null; confirmedSkills: string[];
  profileUpdatedAt: string | null; professionalUpdatedAt: string | null; skillsUpdatedAt: string | null;
};
export type Paged<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number; first: boolean; last: boolean };
