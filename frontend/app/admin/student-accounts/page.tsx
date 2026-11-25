"use client";

import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { StudentAccountsContent } from "@/components/StudentAccountsContent";

export default function StudentAccountsPage() {
  return (
    <ProtectedRoute role="ADMIN">
      <AppFrame>
        <StudentAccountsContent />
      </AppFrame>
    </ProtectedRoute>
  );
}
