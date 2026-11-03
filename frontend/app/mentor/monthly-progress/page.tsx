"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

export default function MentorMonthlyProgressPage() {
  const router = useRouter();
  useEffect(() => {
    router.replace("/mentor/progress?tab=monthly");
  }, [router]);
  return null;
}
