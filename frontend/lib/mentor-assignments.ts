import { ProfileStatus } from "@/lib/student-profile";
import { MentorAccount } from "@/lib/mentor-accounts";

export type AssignmentStudent = {
  userId: number;
  name: string | null;
  uid: string | null;
  branch: string | null;
  branchLabel: string | null;
  semester: number | null;
  profileStatus: ProfileStatus;
  currentMentorUserId: number | null;
  currentMentorName: string | null;
};

export type MentorAssignmentWorkspace = {
  mentor: MentorAccount;
  assignedStudents: AssignmentStudent[];
  availableStudents: AssignmentStudent[];
  totalStudents: number;
  assignedStudentsCount: number;
  unassignedStudentsCount: number;
};
