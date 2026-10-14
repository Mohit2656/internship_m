import { StudentProfile } from "@/lib/student-profile";
import { LastStudentUpdate } from "@/components/LastStudentUpdate";

const Row = ({ label, value }: { label: string; value?: string | number | null }) => <div><dt className="text-xs font-semibold uppercase tracking-wide text-slate-500">{label}</dt><dd className="mt-1 whitespace-pre-wrap text-sm font-medium text-slate-900">{value || "—"}</dd></div>;
const Section = ({ title, children }: { title: string; children: React.ReactNode }) => <section className="rounded-2xl border border-slate-200 bg-white p-6"><h2 className="mb-5 text-lg font-bold text-slate-950">{title}</h2><dl className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">{children}</dl></section>;

export function ProfileDetails({ profile, fullAadhaar }: { profile: StudentProfile; fullAadhaar?: string | null }) {
  return <div className="space-y-5">
    <LastStudentUpdate value={profile.updatedAt ?? profile.createdAt}/>
    <Section title="Personal Information"><Row label="Student Name" value={profile.studentName}/><Row label="Personal Email" value={profile.personalEmail}/><Row label="Phone Number" value={profile.phoneNumber}/><Row label="Blood Group" value={profile.bloodGroup}/></Section>
    <Section title="Academic / Institutional Information"><Row label="Branch" value={profile.branchLabel}/><Row label="UID" value={profile.uid}/><Row label="Roll Number" value={profile.rollNumber}/><Row label="Institute Email" value={profile.instituteEmail}/><Row label="Academic Year" value={profile.academicYear}/><Row label="Semester" value={profile.semester}/></Section>
    <Section title="Address"><Row label="Permanent Address" value={profile.permanentAddress}/><Row label="Current Address During Internship" value={profile.currentAddress}/></Section>
    <Section title="Emergency / Guardian Information"><Row label="Parent / Guardian Name" value={profile.guardianName}/><Row label="Parent / Guardian Contact" value={profile.guardianContact}/><Row label="Emergency Contact" value={profile.emergencyContact}/></Section>
    <Section title="Department Contacts"><Row label="Faculty Mentor" value={profile.facultyMentorName}/><Row label="Faculty Mentor Contact" value={profile.facultyMentorContact}/><Row label="Internship Coordinator" value={profile.internshipCoordinatorName}/><Row label="Internship Coordinator Contact" value={profile.internshipCoordinatorContact}/><Row label="CDC Coordinator" value={profile.cdcCoordinatorName}/><Row label="CDC Coordinator Contact" value={profile.cdcCoordinatorContact}/></Section>
    <Section title="Official Identity"><Row label="Aadhaar Number" value={fullAadhaar ?? profile.maskedAadhaar}/></Section>
  </div>;
}
