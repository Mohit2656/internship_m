export type RegisteredWorkplace = { name?: string | null; placeId: string | null; address: string; latitude: number; longitude: number };

export type CompanyType = "IT" | "MANUFACTURING" | "CORE" | "OTHER";
export type InternshipMode = "OFFICE_REPORTING" | "HYBRID" | "ONLINE" | "COLLEGE_REPORTING";

export type InternshipDetails = {
  workplace?: RegisteredWorkplace | null;
  id: number; onboardingId: number; companyName: string; companyType: CompanyType; companyTypeLabel: string;
  otherCompanyType: string | null; companyAddress: string; companyPhone: string; industrySupervisorName: string;
  industrySupervisorDesignation: string; industrySupervisorContact: string; durationMonths: number; startDate: string;
  endDate: string; totalWeeks: number; internshipMode: InternshipMode; internshipModeLabel: string;
  stipendPerMonth: number; fullTimeEmploymentOffered: boolean; createdAt: string; updatedAt: string;
};

export type InternshipDetailsFormData = {
  workplace: RegisteredWorkplace | null;
  companyName: string; companyType: CompanyType | ""; otherCompanyType: string; companyAddress: string; companyPhone: string;
  industrySupervisorName: string; industrySupervisorDesignation: string; industrySupervisorContact: string;
  durationMonths: string; startDate: string; endDate: string; totalWeeks: string; internshipMode: InternshipMode | "";
  stipendPerMonth: string; fullTimeEmploymentOffered: "" | "true" | "false";
};

export const emptyInternshipDetails: InternshipDetailsFormData = {
  workplace: null,
  companyName: "", companyType: "", otherCompanyType: "", companyAddress: "", companyPhone: "",
  industrySupervisorName: "", industrySupervisorDesignation: "", industrySupervisorContact: "", durationMonths: "",
  startDate: "", endDate: "", totalWeeks: "", internshipMode: "", stipendPerMonth: "", fullTimeEmploymentOffered: "",
};

export function toFormData(details: InternshipDetails): InternshipDetailsFormData {
  return { workplace: details.workplace ?? null, companyName: details.companyName, companyType: details.companyType, otherCompanyType: details.otherCompanyType ?? "",
    companyAddress: details.companyAddress, companyPhone: details.companyPhone, industrySupervisorName: details.industrySupervisorName,
    industrySupervisorDesignation: details.industrySupervisorDesignation, industrySupervisorContact: details.industrySupervisorContact,
    durationMonths: String(details.durationMonths), startDate: details.startDate, endDate: details.endDate,
    totalWeeks: String(details.totalWeeks), internshipMode: details.internshipMode, stipendPerMonth: String(details.stipendPerMonth),
    fullTimeEmploymentOffered: String(details.fullTimeEmploymentOffered) as "true" | "false" };
}
