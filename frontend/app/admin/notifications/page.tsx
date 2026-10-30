"use client";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { NotificationsContent } from "@/components/NotificationsContent";
export default function AdminNotificationsPage() {
  return <ProtectedRoute role="ADMIN"><AppFrame><NotificationsContent role="ADMIN" /></AppFrame></ProtectedRoute>;
}
