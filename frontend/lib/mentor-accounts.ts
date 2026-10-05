export type MentorAccount = {
  id: number;
  userId: number;
  name: string;
  employeeId: string;
  instituteEmail: string;
  department: string;
  designation: string;
  phone: string;
  active: boolean;
  mustChangePassword: boolean;
  assignedStudentCount: number;
  createdAt: string;
};

export type MentorAccountInput = {
  name: string;
  employeeId: string;
  instituteEmail: string;
  department: string;
  designation: string;
  phone: string;
};
