"use client";

import { WorkspaceSidebar, type WorkspaceSidebarGroup, type WorkspaceSidebarProps } from "@/components/WorkspaceSidebar";

const groups: WorkspaceSidebarGroup[] = [
  {
    heading: "Overview",
    items: [
      { href: "/student/dashboard", label: "Dashboard", exact: true },
      { href: "/student/internship", label: "My Internships" },
      { href: "/student/attendance", label: "Attendance History" },
    ],
  },
  {
    heading: "Academics",
    items: [
      {
        href: "/student/progress-reports",
        label: "Reports",
        exact: true,
        children: [
          { href: "/student/weekly-progress", label: "Weekly Report" },
          { href: "/student/monthly-progress", label: "Monthly Report" },
        ],
      },
      { href: "/student/opportunities", label: "Opportunities" },
    ],
  },
  {
    heading: "Account",
    items: [
      { href: "/student/notifications", label: "Notifications" },
    ],
  },
];

export function StudentSidebar(props: WorkspaceSidebarProps) {
  return <WorkspaceSidebar {...props} id="student" label="Student" groups={groups} />;
}
