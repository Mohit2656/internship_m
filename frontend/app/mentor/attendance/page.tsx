"use client";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { AttendanceRecords } from "@/components/AttendanceRecords";
import { DashboardPage } from "@/components/DashboardLayout";
import { LateAttendancePermissions } from "@/components/LateAttendancePermissions";
export default function Page() {
  return <ProtectedRoute role="MENTOR"><AppFrame><DashboardPage eyebrow="Mentor workspace" title="Attendance" description="View individual attendance records, verify submission evidence and manage approval decisions."><AttendanceRecords role="MENTOR" /><section id="late-attendance-permissions" className="scroll-mt-24"><LateAttendancePermissions /></section></DashboardPage></AppFrame></ProtectedRoute>;
}
