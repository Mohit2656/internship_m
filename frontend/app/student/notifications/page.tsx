"use client";

import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { NotificationsContent } from "@/components/NotificationsContent";

export default function StudentNotificationsPage() {
  return <ProtectedRoute role="STUDENT"><AppFrame><NotificationsContent /></AppFrame></ProtectedRoute>;
}
