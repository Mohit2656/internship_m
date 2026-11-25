export type RegisteredWorkplace = { name?: string | null; placeId: string | null; address: string; latitude: number; longitude: number };

export type CompanyType = "IT" | "MANUFACTURING" | "CORE" | "OTHER";
export type InternshipMode = "OFFICE_REPORTING" | "HYBRID" | "ONLINE" | "COLLEGE_REPORTING";

/** Calendar dates are inclusive. UTC arithmetic prevents daylight-saving drift. */
export function internshipWeeks(start: string, end: string): number | null {
  const date = (value: string) => {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return NaN;
    const parsed = new Date(`${value}T00:00:00Z`);
    return Number.isNaN(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== value ? NaN : parsed.getTime();
  };
  const first = date(start), last = date(end);
  return !Number.isFinite(first) || !Number.isFinite(last) || last <= first ? null : Math.ceil(((last - first) / 86400000 + 1) / 7);
}

export type InternshipDetails = {
  workplace?: RegisteredWorkplace | null;
  id: number; onboardingId: number; internshipNumber: number; companyName?: string | null; companyType?: CompanyType | null; companyTypeLabel?: string | null;
  otherCompanyType?: string | null; companyAddress?: string | null; companyPhone?: string | null; industrySupervisorName?: string | null;
  industrySupervisorDesignation?: string | null; industrySupervisorContact?: string | null; durationMonths?: number | null; startDate?: string | null;
  endDate?: string | null; totalWeeks?: number | null; internshipMode?: InternshipMode | null; internshipModeLabel?: string | null;
  stipendPerMonth?: number | null; fullTimeEmploymentOffered?: boolean | null; createdAt: string; updatedAt: string;
  workingDaysPerWeek?: number | null; workingDays?: string[] | null;
  lifecycleStatus?: string | null; terminatedAt?: string | null; terminationReason?: string | null;
  deletedAt?: string | null; deletionReason?: string | null;
};

export type InternshipDetailsFormData = {
  workplace?: RegisteredWorkplace | null;
  companyName: string; companyType: CompanyType | ""; otherCompanyType: string; companyAddress: string; companyPhone: string;
  industrySupervisorName: string; industrySupervisorDesignation: string; industrySupervisorContact: string;
  durationMonths: string; startDate: string; endDate: string; totalWeeks: string; internshipMode: InternshipMode | "";
  stipendPerMonth: string; fullTimeEmploymentOffered: "" | "true" | "false";
  workingDaysPerWeek: string; workingDays: string[];
};

export const emptyInternshipDetails: InternshipDetailsFormData = {
  companyName: "", companyType: "", otherCompanyType: "", companyAddress: "", companyPhone: "",
  industrySupervisorName: "", industrySupervisorDesignation: "", industrySupervisorContact: "", durationMonths: "",
  startDate: "", endDate: "", totalWeeks: "", internshipMode: "", stipendPerMonth: "", fullTimeEmploymentOffered: "",
  workingDaysPerWeek: "5", workingDays: ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
};

export function toFormData(details: InternshipDetails): InternshipDetailsFormData {
  return {
    workplace: details.workplace ?? null,
    companyName: details.companyName ?? "",
    companyType: details.companyType ?? "",
    otherCompanyType: details.otherCompanyType ?? "",
    companyAddress: details.companyAddress ?? "",
    companyPhone: details.companyPhone ?? "",
    industrySupervisorName: details.industrySupervisorName ?? "",
    industrySupervisorDesignation: details.industrySupervisorDesignation ?? "",
    industrySupervisorContact: details.industrySupervisorContact ?? "",
    durationMonths: details.durationMonths != null ? String(details.durationMonths) : "",
    startDate: details.startDate ?? "",
    endDate: details.endDate ?? "",
    totalWeeks: details.totalWeeks != null ? String(details.totalWeeks) : "",
    internshipMode: details.internshipMode ?? "",
    stipendPerMonth: details.stipendPerMonth != null ? String(details.stipendPerMonth) : "",
    fullTimeEmploymentOffered: details.fullTimeEmploymentOffered === true ? "true" : details.fullTimeEmploymentOffered === false ? "false" : "",
    workingDaysPerWeek: details.workingDaysPerWeek != null ? String(details.workingDaysPerWeek) : (details.workingDays && details.workingDays.length > 0 ? String(details.workingDays.length) : "5"),
    workingDays: details.workingDays && details.workingDays.length > 0 ? details.workingDays : ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
  };
}
