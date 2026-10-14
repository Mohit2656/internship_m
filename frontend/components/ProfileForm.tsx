"use client";

import { FormEvent, useEffect, useState } from "react";
import { AxiosError } from "axios";
import { api, ApiError } from "@/lib/api";
import { initialProfileForm, profileReadOnlyFields, ProfileFormData, ProfilePrefill, StudentProfile } from "@/lib/student-profile";

type Options = { branches: { value: string; label: string }[]; bloodGroups: string[]; minimumSemester: number; maximumSemester: number };
type FieldProps = { label: string; name: keyof ProfileFormData; value: string; error?: string; type?: string; optional?: boolean; placeholder?: string; readOnly?: boolean; onChange: (name: keyof ProfileFormData, value: string) => void };
function Field({ label, name, value, error, type = "text", optional, placeholder, readOnly = false, onChange }: FieldProps) {
  return <label className="block"><span className="mb-2 block text-sm font-semibold text-slate-800">{label}{optional && <span className="ml-1 font-normal text-slate-400">(optional)</span>}</span>
    <input name={name} type={type} value={value} readOnly={readOnly} aria-readonly={readOnly || undefined} aria-describedby={readOnly ? `${name}-institutional` : undefined} onChange={readOnly ? undefined : e => onChange(name, e.target.value)} required={!optional} placeholder={placeholder} className={`w-full rounded-xl border px-4 py-3 text-sm outline-none transition focus:ring-4 ${readOnly ? "bg-slate-50 text-slate-800" : "bg-white"} ${error ? "border-red-400 focus:border-red-500 focus:ring-red-100" : "border-slate-300 focus:border-blue-600 focus:ring-blue-100"}`}/>
    {readOnly && <span id={`${name}-institutional`} className="mt-1.5 block text-xs text-slate-500">Institution provided · Read-only</span>}
    {error && <span className="mt-1.5 block text-xs text-red-600">{error}</span>}
  </label>;
}
function FormSection({ title, description, children }: { title: string; description: string; children: React.ReactNode }) {
  return <section className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-7"><div className="mb-6"><h2 className="text-lg font-bold text-slate-950">{title}</h2><p className="mt-1 text-sm text-slate-500">{description}</p></div><div className="grid gap-5 md:grid-cols-2">{children}</div></section>;
}

export function ProfileForm({ existing, institutional, onSaved, onCancel }: { existing?: StudentProfile; institutional?: ProfilePrefill; onSaved: (profile: StudentProfile) => void; onCancel?: () => void }) {
  const [form, setForm] = useState<ProfileFormData>(() => initialProfileForm(existing, institutional));
  const readOnlyFields = profileReadOnlyFields(existing, institutional);
  const [options, setOptions] = useState<Options | null>(null); const [errors, setErrors] = useState<Record<string, string>>({});
  const [message, setMessage] = useState(""); const [saving, setSaving] = useState(false);
  useEffect(() => { api.get<Options>("/student/profile/options").then(r => setOptions(r.data)).catch(() => setMessage("Unable to load profile options.")); }, []);
  function update(name: keyof ProfileFormData, value: string) { if (readOnlyFields.has(name)) return; setForm(current => ({ ...current, [name]: value })); setErrors(current => ({ ...current, [name]: "" })); }
  async function submit(event: FormEvent) {
    event.preventDefault(); setSaving(true); setErrors({}); setMessage("");
    try {
      const payload = { ...form, semester: Number(form.semester) };
      const { data } = existing ? await api.put<StudentProfile>("/student/profile", payload) : await api.post<StudentProfile>("/student/profile", payload);
      onSaved(data);
    } catch (cause) {
      const error = (cause as AxiosError<ApiError>).response?.data; setErrors(error?.fieldErrors ?? {}); setMessage(error?.message ?? "Unable to save your profile. Please try again.");
    } finally { setSaving(false); }
  }
  return <form onSubmit={submit} className="space-y-5">
    <FormSection title="Personal Information" description="Your primary contact and identification details.">
      <Field label="Student Name" name="studentName" value={form.studentName} error={errors.studentName} readOnly={readOnlyFields.has("studentName")} onChange={update}/>
      <Field label="Personal Email ID" name="personalEmail" type="email" value={form.personalEmail} error={errors.personalEmail} onChange={update}/>
      <Field label="Phone Number" name="phoneNumber" type="tel" value={form.phoneNumber} error={errors.phoneNumber} placeholder="+91 98765 43210" onChange={update}/>
      <label className="block"><span className="mb-2 block text-sm font-semibold text-slate-800">Blood Group</span><select required value={form.bloodGroup} onChange={e => update("bloodGroup", e.target.value)} className="w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-100"><option value="">Select blood group</option>{options?.bloodGroups.map(value => <option key={value}>{value}</option>)}</select>{errors.bloodGroup && <span className="mt-1.5 block text-xs text-red-600">{errors.bloodGroup}</span>}</label>
    </FormSection>
    <FormSection title="Academic / Institutional Information" description="Details used by your department and the III Cell.">
      <Field label="Branch" name="branch" value={existing?.branchLabel ?? institutional?.branchLabel ?? "Computer Engineering"} readOnly onChange={update}/>
      <Field label="UID" name="uid" value={form.uid} error={errors.uid} readOnly={readOnlyFields.has("uid")} onChange={update}/><Field label="Roll Number" name="rollNumber" value={form.rollNumber} error={errors.rollNumber} onChange={update}/>
      <Field label="Institute Email ID" name="instituteEmail" type="email" value={form.instituteEmail} error={errors.instituteEmail} readOnly onChange={update}/>
      <Field label="Academic Year" name="academicYear" value={form.academicYear} error={errors.academicYear} placeholder="2026-2027" onChange={update}/>
      {readOnlyFields.has("semester") ? <Field label="Semester" name="semester" value={form.semester} error={errors.semester} readOnly onChange={update}/> : <label className="block"><span className="mb-2 block text-sm font-semibold text-slate-800">Semester</span><select name="semester" required value={form.semester} onChange={e => update("semester", e.target.value)} className="w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-100"><option value="">Select semester</option>{Array.from({ length: (options?.maximumSemester ?? 8) }, (_, i) => i + 1).map(value => <option key={value} value={value}>{value}</option>)}</select>{errors.semester && <span className="mt-1.5 block text-xs text-red-600">{errors.semester}</span>}</label>}
    </FormSection>
    <FormSection title="Address" description="Current internship address can be added later if it is not yet known.">
      {([['Permanent Address','permanentAddress',false],['Current Address During Internship','currentAddress',true]] as const).map(([label,name,optional]) => <label key={name} className="block"><span className="mb-2 block text-sm font-semibold text-slate-800">{label}{optional && <span className="ml-1 font-normal text-slate-400">(optional)</span>}</span><textarea rows={4} required={!optional} value={form[name]} onChange={e => update(name,e.target.value)} className="w-full resize-y rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-100"/>{errors[name] && <span className="mt-1.5 block text-xs text-red-600">{errors[name]}</span>}</label>)}
    </FormSection>
    <FormSection title="Emergency / Guardian Information" description="Contacts the institution may use when necessary.">
      <Field label="Parent / Guardian Name" name="guardianName" value={form.guardianName} error={errors.guardianName} onChange={update}/><Field label="Parent / Guardian Contact Number" name="guardianContact" type="tel" value={form.guardianContact} error={errors.guardianContact} onChange={update}/><Field label="Emergency Contact Number" name="emergencyContact" type="tel" value={form.emergencyContact} error={errors.emergencyContact} onChange={update}/>
    </FormSection>
    <FormSection title="Department Contacts" description="Your designated departmental faculty contacts.">
      <Field label="Faculty Mentor Name" name="facultyMentorName" value={form.facultyMentorName} error={errors.facultyMentorName} onChange={update}/><Field label="Faculty Mentor Contact Number" name="facultyMentorContact" type="tel" value={form.facultyMentorContact} error={errors.facultyMentorContact} onChange={update}/><Field label="Faculty Internship Coordinator Name" name="internshipCoordinatorName" value={form.internshipCoordinatorName} error={errors.internshipCoordinatorName} onChange={update}/><Field label="Faculty Internship Coordinator Contact" name="internshipCoordinatorContact" type="tel" value={form.internshipCoordinatorContact} error={errors.internshipCoordinatorContact} onChange={update}/><Field label="Department CDC Coordinator Name" name="cdcCoordinatorName" value={form.cdcCoordinatorName} error={errors.cdcCoordinatorName} onChange={update}/><Field label="Department CDC Coordinator Contact" name="cdcCoordinatorContact" type="tel" value={form.cdcCoordinatorContact} error={errors.cdcCoordinatorContact} onChange={update}/>
    </FormSection>
    <FormSection title="Official Identity" description="Your Aadhaar number is encrypted before storage and is shown only in masked form.">
      <Field label="Aadhaar Number" name="aadhaarNumber" type="password" value={form.aadhaarNumber} error={errors.aadhaarNumber} optional={Boolean(existing)} placeholder={existing ? "Leave blank to keep the saved Aadhaar number" : undefined} onChange={update}/>
    </FormSection>
    {message && <p role="alert" className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{message}</p>}
    <div className="flex justify-end gap-3 rounded-2xl border border-slate-200 bg-white p-5">{onCancel && <button data-button-variant="outline" type="button" onClick={onCancel} className="text-sm">Cancel</button>}<button disabled={saving} className="text-sm">{saving ? "Saving…" : existing ? "Save Changes" : "Complete Profile"}</button></div>
  </form>;
}
