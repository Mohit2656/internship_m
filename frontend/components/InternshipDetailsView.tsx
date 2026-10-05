import { InternshipDetails } from "@/lib/internship-details";

export function InternshipDetailsView({ details }: { details: InternshipDetails }) {
  const companyType = details.companyType === "OTHER" ? details.otherCompanyType ?? "Other" : details.companyTypeLabel;
  return <section className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-8"><div><p className="text-xs font-bold uppercase tracking-[0.18em] text-blue-700">Internship Details</p><h2 className="mt-2 text-xl font-bold">{details.companyName}</h2></div>
    <div className="mt-7 grid gap-7 md:grid-cols-2"><Group title="Company Information" values={[["Company Name", details.companyName], ["Company Type", companyType], ["Company Address", details.companyAddress], ["Company Phone", details.companyPhone]]}/><Group title="Industry Supervisor" values={[["Supervisor Name", details.industrySupervisorName], ["Designation", details.industrySupervisorDesignation], ["Contact Number", details.industrySupervisorContact]]}/><Group title="Internship Duration" values={[["Duration", `${details.durationMonths} months`], ["Start Date", details.startDate], ["End Date", details.endDate], ["Total Weeks", String(details.totalWeeks)]]}/><Group title="Internship Arrangement" values={[["Mode", details.internshipModeLabel], ["Stipend Per Month", Number(details.stipendPerMonth) === 0 ? "Unpaid" : `₹${Number(details.stipendPerMonth).toLocaleString("en-IN")}`], ["Full-Time Employment Opportunity", details.fullTimeEmploymentOffered ? "Yes" : "No"]]}/></div>
  </section>;
}

function Group({ title, values }: { title: string; values: [string, string][] }) { return <div><h3 className="font-bold text-slate-900">{title}</h3><dl className="mt-3 space-y-3">{values.map(([label, value]) => <div key={label}><dt className="text-xs font-bold uppercase tracking-wide text-slate-500">{label}</dt><dd className="mt-1 whitespace-pre-wrap text-sm text-slate-800">{value}</dd></div>)}</dl></div>; }
