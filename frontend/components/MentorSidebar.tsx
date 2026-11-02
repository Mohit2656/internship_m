"use client";

import { WorkspaceSidebar, type WorkspaceSidebarGroup, type WorkspaceSidebarProps } from "@/components/WorkspaceSidebar";

const groups: WorkspaceSidebarGroup[] = [
  { heading: "Overview", items: [
    { href: "/mentor/dashboard", label: "Home", exact: true, inactiveWithHash: true },
    { href: "/mentor/dashboard#assigned-students", label: "Assigned Students", section: "assigned-students", nested: "/mentor/students" },
  ] },
  { heading: "Attendance", items: [
    { href: "/mentor/attendance", label: "Attendance", exact: true, inactiveWithHash: true },
    { href: "/mentor/attendance-report", label: "Attendance Report" },
    { href: "/mentor/attendance#late-attendance-permissions", label: "Late Attendance Requests", section: "late-attendance-permissions" },
  ] },
  { heading: "Internship Management", items: [
    { href: "/mentor/weekly-progress", label: "Reports", children: [
      { href: "/mentor/weekly-progress", label: "Weekly Report" },
      { href: "/mentor/monthly-progress", label: "Monthly Report" },
    ] },
    { href: "/mentor/documents", label: "Documents" },
    { href: "/mentor/reports", label: "Report Center" },
  ] },
  { heading: "Account", items: [
    { href: "/mentor/notifications", label: "Notifications" },
    { href: "/mentor/change-password", label: "Change Password" },
  ] },
];

export function MentorSidebar(props: WorkspaceSidebarProps) {
  return <WorkspaceSidebar {...props} id="mentor" label="Mentor" groups={groups} />;
}
