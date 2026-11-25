import { InternshipOnboarding, InternshipSource, WorkflowStatus } from "@/lib/internship-onboarding";
import { InternshipDetails } from "@/lib/internship-details";

export type ProfileStatus = "INCOMPLETE" | "COMPLETE";

export type StudentProfile = {
  id: number;
  studentName: string | null;
  branch: string | null;
  branchLabel: string | null;
  uid: string | null;
  rollNumber: string | null;
  permanentAddress: string | null;
  currentAddress: string | null;
  personalEmail: string | null;
  instituteEmail: string | null;
  phoneNumber: string | null;
  guardianName: string | null;
  guardianContact: string | null;
  emergencyContact: string | null;
  bloodGroup: string | null;
  maskedAadhaar: string | null;
  facultyMentorName: string | null;
  facultyMentorContact: string | null;
  internshipCoordinatorName: string | null;
  internshipCoordinatorContact: string | null;
  cdcCoordinatorName: string | null;
  cdcCoordinatorContact: string | null;
  academicYear: string | null;
  semester: number | null;
  currentCgpa: number | null;
  profileStatus: ProfileStatus;
  createdAt: string;
  updatedAt: string;
};

export type ProfilePrefill = {
  studentName: string | null;
  uid: string | null;
  instituteEmail: string;
  branch: string;
  branchLabel: string;
  semester: number | null;
  facultyMentorName: string | null;
  facultyMentorContact: string | null;
  readOnlyFields: Array<keyof ProfileFormData>;
};

export type ProfileFormData = {
  studentName: string;
  branch: string;
  uid: string;
  rollNumber: string;
  permanentAddress: string;
  currentAddress: string;
  personalEmail: string;
  instituteEmail: string;
  phoneNumber: string;
  guardianName: string;
  guardianContact: string;
  emergencyContact: string;
  bloodGroup: string;
  aadhaarNumber: string;
  facultyMentorName: string;
  facultyMentorContact: string;
  internshipCoordinatorName: string;
  internshipCoordinatorContact: string;
  cdcCoordinatorName: string;
  cdcCoordinatorContact: string;
  academicYear: string;
  semester: string;
  currentCgpa: string;
};

export const emptyProfile: ProfileFormData = {
  studentName: "",
  branch: "",
  uid: "",
  rollNumber: "",
  permanentAddress: "",
  currentAddress: "",
  personalEmail: "",
  instituteEmail: "",
  phoneNumber: "",
  guardianName: "",
  guardianContact: "",
  emergencyContact: "",
  bloodGroup: "",
  aadhaarNumber: "",
  facultyMentorName: "",
  facultyMentorContact: "",
  internshipCoordinatorName: "",
  internshipCoordinatorContact: "",
  cdcCoordinatorName: "",
  cdcCoordinatorContact: "",
  academicYear: "",
  semester: "",
  currentCgpa: "",
};

export function initialProfileForm(existing?: StudentProfile, institutional?: ProfilePrefill): ProfileFormData {
  if (existing) {
    return {
      ...emptyProfile,
      studentName: existing.studentName ?? institutional?.studentName ?? "",
      branch: existing.branch ?? institutional?.branch ?? "COMPUTER_ENGINEERING",
      uid: existing.uid ?? institutional?.uid ?? "",
      rollNumber: existing.rollNumber ?? existing.uid ?? institutional?.uid ?? "",
      permanentAddress: existing.permanentAddress ?? "",
      currentAddress: existing.currentAddress ?? "",
      personalEmail: existing.personalEmail ?? "",
      instituteEmail: existing.instituteEmail ?? institutional?.instituteEmail ?? "",
      phoneNumber: existing.phoneNumber ?? "",
      guardianName: existing.guardianName ?? "",
      guardianContact: existing.guardianContact ?? "",
      emergencyContact: existing.emergencyContact ?? "",
      bloodGroup: existing.bloodGroup ?? "",
      aadhaarNumber: "",
      facultyMentorName: existing.facultyMentorName ?? institutional?.facultyMentorName ?? "",
      facultyMentorContact: existing.facultyMentorContact ?? institutional?.facultyMentorContact ?? "",
      internshipCoordinatorName: existing.internshipCoordinatorName ?? "",
      internshipCoordinatorContact: existing.internshipCoordinatorContact ?? "",
      cdcCoordinatorName: existing.cdcCoordinatorName ?? "",
      cdcCoordinatorContact: existing.cdcCoordinatorContact ?? "",
      academicYear: existing.academicYear ?? "",
      semester: existing.semester != null ? String(existing.semester) : institutional?.semester != null ? String(institutional.semester) : "",
      currentCgpa: existing.currentCgpa == null ? "" : String(existing.currentCgpa),
    };
  }
  return {
    ...emptyProfile,
    studentName: institutional?.studentName ?? "",
    branch: institutional?.branch ?? "COMPUTER_ENGINEERING",
    uid: institutional?.uid ?? "",
    rollNumber: institutional?.uid ?? "",
    instituteEmail: institutional?.instituteEmail ?? "",
    semester: institutional?.semester == null ? "" : String(institutional.semester),
    facultyMentorName: institutional?.facultyMentorName ?? "",
    facultyMentorContact: institutional?.facultyMentorContact ?? "",
  };
}

export function profileReadOnlyFields(existing?: StudentProfile, institutional?: ProfilePrefill): Set<keyof ProfileFormData> {
  return new Set<keyof ProfileFormData>();
}

export type StudentSummary = {
  id: number;
  loginEmail: string;
  studentName: string | null;
  uid: string | null;
  rollNumber: string | null;
  branch: string | null;
  branchLabel: string | null;
  instituteEmail: string | null;
  phoneNumber: string | null;
  academicYear: string | null;
  semester: number | null;
  profileStatus: ProfileStatus;
  internshipStatus: WorkflowStatus;
  internshipSource: InternshipSource | null;
  internshipSourceLabel: string | null;
  documentsCompleted: number;
  documentsRequired: number;
  diaryIssued: boolean;
  profileLastStudentUpdatedAt: string | null;
  onboardingLastStudentUpdatedAt: string | null;
  resumeUploadedAt: string | null;
  internshipDetailsUpdatedAt: string | null;
  workplaceUpdatedAt: string | null;
};

export type StudentDetail = {
  id: number;
  loginEmail: string;
  profileStatus: ProfileStatus;
  profile: StudentProfile | null;
  aadhaarNumber: string | null;
  internshipOnboarding: InternshipOnboarding;
  internshipDetails: InternshipDetails | null;
};

export type MentorStudentProfile = {
  studentName: string;
  uid: string;
  branch: string;
  branchLabel: string;
  rollNumber: string;
  instituteEmail: string;
  personalEmail: string;
  phoneNumber: string;
  academicYear: string;
  semester: number;
  professionalHeadline: string | null;
  careerInterests: string | null;
  confirmedSkills: string[];
  profileUpdatedAt: string | null;
  professionalUpdatedAt: string | null;
  skillsUpdatedAt: string | null;
};

export type Paged<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};
