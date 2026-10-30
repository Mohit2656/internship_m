import { InternshipOnboarding, InternshipSource, WorkflowStatus } from "@/lib/internship-onboarding";
import { InternshipDetails } from "@/lib/internship-details";

export type ProfileStatus = "INCOMPLETE" | "COMPLETE";

export type StudentProfile = {
  id: number; studentName: string; branch: string; branchLabel: string; uid: string; rollNumber: string;
  permanentAddress: string; currentAddress: string | null; personalEmail: string; instituteEmail: string; phoneNumber: string;
  guardianName: string; guardianContact: string; emergencyContact: string; bloodGroup: string; maskedAadhaar: string;
  facultyMentorName: string; facultyMentorContact: string; internshipCoordinatorName: string; internshipCoordinatorContact: string;
  cdcCoordinatorName: string; cdcCoordinatorContact: string; academicYear: string; semester: number; profileStatus: ProfileStatus;
  createdAt: string; updatedAt: string;
};

export type ProfilePrefill = {
  studentName: string | null; uid: string | null; instituteEmail: string; branch: string; branchLabel: string;
  semester: number | null; readOnlyFields: Array<keyof ProfileFormData>;
};

export type ProfileFormData = Omit<StudentProfile, "id" | "branchLabel" | "maskedAadhaar" | "profileStatus" | "createdAt" | "updatedAt" | "semester" | "currentAddress"> & {
  currentAddress: string; semester: string; aadhaarNumber: string;
};

export const emptyProfile: ProfileFormData = {
  studentName: "", branch: "", uid: "", rollNumber: "", permanentAddress: "", currentAddress: "", personalEmail: "",
  instituteEmail: "", phoneNumber: "", guardianName: "", guardianContact: "", emergencyContact: "", bloodGroup: "",
  aadhaarNumber: "", facultyMentorName: "", facultyMentorContact: "", internshipCoordinatorName: "",
  internshipCoordinatorContact: "", cdcCoordinatorName: "", cdcCoordinatorContact: "", academicYear: "", semester: ""
};

export function initialProfileForm(existing?: StudentProfile, institutional?: ProfilePrefill): ProfileFormData {
  if (existing) return { ...emptyProfile, ...existing, currentAddress: existing.currentAddress ?? "", semester: String(existing.semester), aadhaarNumber: "" };
  return { ...emptyProfile, studentName: institutional?.studentName ?? "", branch: institutional?.branch ?? "COMPUTER_ENGINEERING",
    uid: institutional?.uid ?? "", instituteEmail: institutional?.instituteEmail ?? "",
    semester: institutional?.semester == null ? "" : String(institutional.semester) };
}

export function profileReadOnlyFields(existing?: StudentProfile, institutional?: ProfilePrefill): Set<keyof ProfileFormData> {
  const fields = new Set<keyof ProfileFormData>(institutional?.readOnlyFields ?? ["branch", "instituteEmail"]);
  if (existing) fields.add("uid");
  return fields;
}

export type StudentSummary = {
  id: number; loginEmail: string; studentName: string | null; uid: string | null; rollNumber: string | null; branch: string | null;
  branchLabel: string | null; instituteEmail: string | null; phoneNumber: string | null; academicYear: string | null;
  semester: number | null; profileStatus: ProfileStatus; internshipStatus: WorkflowStatus; internshipSource: InternshipSource | null;
  internshipSourceLabel: string | null; documentsCompleted: number; documentsRequired: number; diaryIssued: boolean;
  profileLastStudentUpdatedAt: string | null; onboardingLastStudentUpdatedAt: string | null;
  resumeUploadedAt: string | null; internshipDetailsUpdatedAt: string | null; workplaceUpdatedAt: string | null;
};
export type StudentDetail = { id: number; loginEmail: string; profileStatus: ProfileStatus; profile: StudentProfile | null; aadhaarNumber: string | null; internshipOnboarding: InternshipOnboarding; internshipDetails: InternshipDetails | null };
export type MentorStudentProfile = {
  studentName: string; uid: string; branch: string; branchLabel: string; rollNumber: string;
  instituteEmail: string; personalEmail: string; phoneNumber: string; academicYear: string; semester: number;
  professionalHeadline: string | null; careerInterests: string | null; confirmedSkills: string[];
  profileUpdatedAt: string | null; professionalUpdatedAt: string | null; skillsUpdatedAt: string | null;
};
export type Paged<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number; first: boolean; last: boolean };
