"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

export default function MentorWeeklyProgressPage() {
  const router = useRouter();
  useEffect(() => {
    router.replace("/mentor/progress?tab=weekly");
  }, [router]);
  return null;
}
