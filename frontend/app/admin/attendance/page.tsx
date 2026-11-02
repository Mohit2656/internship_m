"use client";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { AttendanceRecords } from "@/components/AttendanceRecords";
import { DashboardPage } from "@/components/DashboardLayout";
import { LateAttendancePermissions } from "@/components/LateAttendancePermissions";
export default function Page() { return <ProtectedRoute role="ADMIN"><AppFrame><DashboardPage eyebrow="IIIC workspace" title="Attendance" description="View individual attendance records, original submission evidence and mentor approval decisions."><AttendanceRecords role="ADMIN" /><section id="late-attendance-requests" className="scroll-mt-24"><LateAttendancePermissions role="ADMIN" /></section></DashboardPage></AppFrame></ProtectedRoute>; }
