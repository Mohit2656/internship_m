export function LoadingSpinner({ label, size = "sm" }: { label: string; size?: "sm" | "md" }) {
  return <span role="status" className="inline-flex items-center gap-2" aria-live="polite">
    <span aria-hidden="true" className={`${size === "sm" ? "h-4 w-4 border-2" : "h-5 w-5 border-[3px]"} inline-block shrink-0 animate-spin rounded-full border-current border-r-transparent`} />
    <span>{label}</span>
  </span>;
}
