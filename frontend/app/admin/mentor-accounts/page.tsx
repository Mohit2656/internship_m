"use client";

import { AppFrame } from "@/components/AppFrame";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { MentorAccountsContent } from "@/components/MentorAccountsContent";

export default function MentorAccountsPage() {
  return (
    <ProtectedRoute role="ADMIN">
      <AppFrame>
        <MentorAccountsContent />
      </AppFrame>
    </ProtectedRoute>
  );
}
