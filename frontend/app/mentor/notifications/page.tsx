"use client";

import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { NotificationsContent } from "@/components/NotificationsContent";

export default function MentorNotificationsPage() {
  return <ProtectedRoute role="MENTOR"><AppFrame><NotificationsContent role="MENTOR" /></AppFrame></ProtectedRoute>;
}
