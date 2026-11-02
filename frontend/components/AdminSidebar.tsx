"use client";

import { WorkspaceSidebar, type WorkspaceSidebarGroup, type WorkspaceSidebarProps } from "@/components/WorkspaceSidebar";

const groups: WorkspaceSidebarGroup[] = [
  { heading: "Overview", items: [
    { href: "/admin/dashboard", label: "Home", exact: true, inactiveWithHash: true },
    { href: "/admin/students", label: "Student Directory" },
    { href: "/admin/dashboard#second-internship-requests", label: "Second Internship Requests", section: "second-internship-requests" },
  ] },
  { heading: "Internship Management", items: [
    { href: "/admin/opportunities", label: "Opportunities" },
    { href: "/admin/weekly-progress", label: "Reports", children: [
      { href: "/admin/weekly-progress", label: "Weekly Report" },
      { href: "/admin/monthly-progress", label: "Monthly Report" },
    ] },
    { href: "/admin/attendance", label: "Attendance" },
    { href: "/admin/attendance-report", label: "Attendance Report" },
    { href: "/admin/internship-completions", label: "Completions" },
    { href: "/admin/reports", label: "Report Center" },
  ] },
  { heading: "User Management", items: [
    { href: "/admin/student-accounts", label: "Student Accounts" },
    { href: "/admin/mentor-accounts", label: "Mentor Accounts" },
  ] },
  { heading: "Administration", items: [
    { href: "/admin/settings/academic-sessions", label: "Academic Sessions" },
    { href: "/admin/notifications", label: "Notifications" },
  ] },
];

export function AdminSidebar(props: WorkspaceSidebarProps) {
  return <WorkspaceSidebar {...props} id="admin" label="III Cell" groups={groups} />;
}
