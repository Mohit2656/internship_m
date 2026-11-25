import { InternshipDetails } from "@/lib/internship-details";
import { LastStudentUpdate } from "@/components/LastStudentUpdate";

export function InternshipDetailsView({ details }: { details: InternshipDetails }) {
  const companyType = details.companyType === "OTHER" ? details.otherCompanyType ?? "Other" : (details.companyTypeLabel || details.companyType || "Not specified");
  return <section className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-8"><div><p className="text-xs font-bold uppercase tracking-[0.18em] text-blue-700">Internship Details</p><h2 className="mt-2 text-xl font-bold">{details.companyName || "Untitled Internship"}</h2></div>
    <LastStudentUpdate value={details.updatedAt ?? details.createdAt}/>
    <div className="mt-7 grid gap-7 md:grid-cols-2">
      <Group title="Company Information" values={[
        ["Company Name", details.companyName || "Not specified"],
        ["Company Type", companyType],
        ["Company Address", details.companyAddress || "Not specified"],
        ["Company Phone", details.companyPhone || "Not specified"]
      ]}/>
      <Group title="Industry Supervisor" values={[
        ["Supervisor Name", details.industrySupervisorName || "Not specified"],
        ["Designation", details.industrySupervisorDesignation || "Not specified"],
        ["Contact Number", details.industrySupervisorContact || "Not specified"]
      ]}/>
      <Group title="Internship Duration" values={[
        ["Duration", details.durationMonths != null ? `${details.durationMonths} months` : "Not specified"],
        ["Start Date", details.startDate || "Not specified"],
        ["End Date", details.endDate || "Not specified"],
        ["Total Weeks", details.totalWeeks != null ? String(details.totalWeeks) : "Not calculated"]
      ]}/>
      <Group title="Internship Arrangement" values={[
        ["Mode", details.internshipModeLabel || details.internshipMode || "Not specified"],
        ["Stipend Per Month", details.stipendPerMonth != null ? (Number(details.stipendPerMonth) === 0 ? "Unpaid" : `₹${Number(details.stipendPerMonth).toLocaleString("en-IN")}`) : "Not specified"],
        ["Full-Time Employment Opportunity", details.fullTimeEmploymentOffered === true ? "Yes" : details.fullTimeEmploymentOffered === false ? "No" : "Not specified"]
      ]}/>
      <Group title="Company Working Schedule" values={[
        ["Working Weekdays", details.workingDays && details.workingDays.length > 0
          ? `${details.workingDays.map(d => d.charAt(0) + d.slice(1).toLowerCase()).join(", ")} (${details.workingDaysPerWeek ?? details.workingDays.length} days/week)`
          : "Monday – Friday (5 days/week) [Default schedule]"],
        ["Days Per Week", `${details.workingDaysPerWeek ?? details.workingDays?.length ?? 5} days`]
      ]}/>
    </div>
  </section>;
}

function Group({ title, values }: { title: string; values: [string, string][] }) { return <div><h3 className="font-bold text-slate-900">{title}</h3><dl className="mt-3 space-y-3">{values.map(([label, value]) => <div key={label}><dt className="text-xs font-bold uppercase tracking-wide text-slate-500">{label}</dt><dd className="mt-1 whitespace-pre-wrap text-sm text-slate-800">{value}</dd></div>)}</dl></div>; }
