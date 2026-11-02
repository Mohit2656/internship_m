import type { ReactNode } from "react";

type DashboardPageProps = {
  eyebrow: string;
  title: string;
  description?: string;
  actions?: ReactNode;
  children: ReactNode;
};

// Keep the Student Dashboard's existing page rhythm as the shared reference.
export function DashboardPage({ eyebrow, title, description, actions, children }: DashboardPageProps) {
  return (
    <main className="dashboard-page mx-auto min-w-0 max-w-7xl px-5 py-8 sm:px-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="min-w-0">
          <p className="text-sm font-semibold text-blue-700">{eyebrow}</p>
          <h1 className="mt-1 break-words text-3xl font-bold tracking-tight text-slate-950">{title}</h1>
          {description && <p className="mt-1 text-sm text-slate-600">{description}</p>}
        </div>
        {actions && <div className="flex min-w-0 flex-wrap items-center gap-3">{actions}</div>}
      </div>
      {children}
    </main>
  );
}

type DashboardSectionProps = {
  title: string;
  description?: string;
  children: ReactNode;
  id?: string;
  className?: string;
};

export function DashboardSection({ title, description, children, id, className = "" }: DashboardSectionProps) {
  return (
    <section id={id} className={`min-w-0 scroll-mt-24 rounded-2xl border border-slate-200 bg-white p-6 sm:p-7 ${className}`}>
      <h2 className="text-lg font-bold text-slate-900">{title}</h2>
      {description && <p className="mt-1 text-sm text-slate-600">{description}</p>}
      {children}
    </section>
  );
}

type DashboardMetricProps = {
  label: string;
  value: number | string;
  tone?: "slate" | "blue" | "amber" | "emerald";
};

const metricTones = {
  slate: "border-slate-200 bg-white",
  blue: "border-blue-200 bg-blue-50/40",
  amber: "border-amber-200 bg-amber-50/50",
  emerald: "border-emerald-200 bg-emerald-50/50",
};

export function DashboardMetric({ label, value, tone = "slate" }: DashboardMetricProps) {
  return (
    <article className={`min-w-0 rounded-2xl border p-5 ${metricTones[tone]}`}>
      <p className="break-words text-xs font-bold uppercase tracking-[0.14em] text-slate-500">{label}</p>
      <p className="mt-2 break-words text-3xl font-bold tracking-tight text-slate-900">{value}</p>
    </article>
  );
}
