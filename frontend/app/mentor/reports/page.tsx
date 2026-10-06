"use client";
import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { ReportsCenter } from "@/components/ReportsCenter";
export default function Page() { return <ProtectedRoute role="MENTOR"><AppFrame><ReportsCenter role="MENTOR" attendanceOnly={false} /></AppFrame></ProtectedRoute>; }
