export type Breakdown = { value: string; label: string; count: number };

export type DashboardSummary = {
  students: { total: number; profileComplete: number; profileIncomplete: number };
  internshipProgress: { secured: number; notSecured: number; documentsPending: number; documentsComplete: number; diaryIssued: number; detailsComplete: number };
  sources: Breakdown[]; companyTypes: Breakdown[]; internshipModes: Breakdown[];
  stipend: { paid: number; unpaid: number; averagePaidMonthlyStipend: number | null };
  fullTimeEmployment: { yes: number; no: number };
  topCompanies: { companyName: string; students: number }[];
  attention: { profileIncomplete: number; internshipNotSecured: number; documentsPending: number; diaryPending: number; internshipDetailsPending: number };
};
