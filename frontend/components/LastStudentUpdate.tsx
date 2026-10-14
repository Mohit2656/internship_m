export function LastStudentUpdate({ value, className = "mt-3 text-xs text-slate-500" }: { value: string | null | undefined; className?: string }) {
  const date = value ? new Date(value) : null;
  const available = date !== null && !Number.isNaN(date.getTime());
  return <p className={className}>Last updated by student: {available ? <time dateTime={value!}>{new Intl.DateTimeFormat("en-IN", { dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Kolkata" }).format(date)} IST</time> : "Not updated yet"}</p>;
}
