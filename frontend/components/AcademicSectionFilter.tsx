"use client";

export function academicSectionLabel(section?: string | null) {
  return section === "A" || section === "B" ? `Section ${section}` : "Section needs review";
}

export function AcademicSectionFilter({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  return <label className="block text-sm font-semibold text-slate-800">Section
    <select aria-label="Section" value={value} onChange={event => onChange(event.target.value)} className="input mt-2">
      <option value="">All sections</option><option value="A">Section A</option><option value="B">Section B</option><option value="UNASSIGNED">Section needs review</option>
    </select>
  </label>;
}
