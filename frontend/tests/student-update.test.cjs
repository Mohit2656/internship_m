const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');

function load(file, imports = {}) {
  const module = { exports: {} };
  const source = ts.transpileModule(fs.readFileSync(path.join(__dirname, '..', file), 'utf8'), {
    compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
  }).outputText;
  new Function('require', 'module', 'exports', source)(name => imports[name] || require(name), module, module.exports);
  return module.exports;
}
const update = load('components/LastStudentUpdate.tsx');
const imports = { '@/components/LastStudentUpdate': update };
const timestamp = '2026-10-01T08:00:00Z';

test('shared report formatter matches student timestamps in IST across viewer timezones', () => {
  const { formatDateTime } = load('lib/monthly-progress.ts', { '@/lib/api': {} });
  assert.equal(formatDateTime(timestamp), '1 Oct 2026, 1:30 pm IST');
  assert.equal(formatDateTime(null), 'Not available');
  assert.equal(formatDateTime('invalid'), 'Not available');
});

test('student timestamp exposes the server datetime and an explicit unavailable fallback', () => {
  const html = renderToStaticMarkup(React.createElement(update.LastStudentUpdate, { value: timestamp }));
  assert.match(html, /Last updated by student:/);
  assert.match(html, /dateTime="2026-10-01T08:00:00Z"/i);
  assert.match(html, /1 Oct 2026/);
  assert.match(html, /1:30 pm IST/i);
  for (const value of [null, undefined, '', 'invalid']) {
    assert.match(renderToStaticMarkup(React.createElement(update.LastStudentUpdate, { value })), /Not updated yet/);
  }
});

test('profile display uses its own saved timestamp and accurate creation fallback', () => {
  const { ProfileDetails } = load('components/ProfileDetails.tsx', imports);
  for (const profile of [{ updatedAt: timestamp }, { updatedAt: null, createdAt: timestamp }]) {
    const html = renderToStaticMarkup(React.createElement(ProfileDetails, { profile }));
    assert.match(html, /dateTime="2026-10-01T08:00:00Z"/i);
  }
});

test('internship details display their own last student edit', () => {
  const { InternshipDetailsView } = load('components/InternshipDetailsView.tsx', imports);
  const html = renderToStaticMarkup(React.createElement(InternshipDetailsView, { details: { companyName: 'Company', updatedAt: timestamp } }));
  assert.match(html, /dateTime="2026-10-01T08:00:00Z"/i);
});

test('mentor and admin document views retain independent onboarding and document timestamps', () => {
  const { InternshipOnboardingDetails } = load('components/InternshipOnboardingDetails.tsx', {
    ...imports, react: { useState: value => [value, () => {}] },
    '@/components/DocumentPreviewModal': { DocumentPreviewModal: () => null },
    '@/components/WorkflowBadge': { WorkflowBadge: () => null },
    '@/lib/internship-onboarding': { fileSize: () => '1 KB' },
  });
  const onboarding = { lastStudentUpdatedAt: timestamp, updatedAt: '2026-10-14T08:00:00Z', documents: [
    { documentType: 'OFFER_LETTER', documentTypeLabel: 'Offer letter', uploaded: true, id: 1, uploadedAt: '2026-09-30T08:00:00Z' },
    { documentType: 'UNDERTAKING', documentTypeLabel: 'Undertaking', uploaded: false, uploadedAt: null },
  ] };
  for (const documentBasePath of ['/mentor/students/1/internship-onboarding/documents', '/admin/students/1/internship-onboarding/documents']) {
    const html = renderToStaticMarkup(React.createElement(InternshipOnboardingDetails, { onboarding, documentBasePath }));
    assert.match(html, /dateTime="2026-10-01T08:00:00Z"/i);
    assert.match(html, /dateTime="2026-09-30T08:00:00Z"/i);
    assert.doesNotMatch(html, /dateTime="2026-10-14T08:00:00Z"/i);
    assert.equal((html.match(/Last updated by student:/g) || []).length, 2);
  }
});

test('admin professional view shows distinct profile, resume, and skill timestamps', () => {
  let index = 0;
  const states = [{ updatedAt: timestamp, skillsUpdatedAt: '2026-10-02T08:00:00Z', resume: {
    uploadedAt: '2026-10-03T08:00:00Z', originalFilename: 'resume.pdf', fileSize: 1,
  } }, [{ id: 1, displayName: 'Java' }], false, false, ''];
  const { AdminProfessionalProfile } = load('components/AdminProfessionalProfile.tsx', {
    ...imports, react: { useEffect() {}, useState: () => [states[index++], () => {}] },
    '@/components/ResumeCorrectionEditor': { ResumeCorrectionEditor: () => null },
    '@/lib/api': {}, '@/lib/professional-profile': { readableFileSize: () => '1 KB' },
  });
  const html = renderToStaticMarkup(React.createElement(AdminProfessionalProfile, { studentId: 1 }));
  for (const value of [timestamp, '2026-10-02T08:00:00Z', '2026-10-03T08:00:00Z']) {
    assert.ok(html.includes(`dateTime="${value}"`));
  }
  assert.equal((html.match(/Last updated by student:/g) || []).length, 3);
});

test('mentor professional view keeps independent student profile, professional profile, and skill times', () => {
  let index = 0;
  const states = [{ studentName: 'Student', confirmedSkills: ['Java'], profileUpdatedAt: timestamp,
    professionalUpdatedAt: '2026-10-02T08:00:00Z', skillsUpdatedAt: '2026-10-03T08:00:00Z' }, ''];
  const wrap = ({ children }) => children;
  const { default: MentorStudentProfileRoute } = load('app/mentor/students/[id]/page.tsx', {
    ...imports, react: { useEffect() {}, useState: () => [states[index++], () => {}] },
    'next/navigation': { useParams: () => ({ id: '1' }) }, 'next/link': { default: wrap },
    '@/lib/api': {}, '@/components/AppFrame': { AppFrame: wrap }, '@/components/ProtectedRoute': { ProtectedRoute: wrap },
  });
  const html = renderToStaticMarkup(React.createElement(MentorStudentProfileRoute));
  for (const value of [timestamp, '2026-10-02T08:00:00Z', '2026-10-03T08:00:00Z']) {
    assert.ok(html.includes(`dateTime="${value}"`));
  }
  assert.equal((html.match(/Last updated by student:/g) || []).length, 3);
});

test('mentor attendance history shows the authoritative timestamp only for student submissions', () => {
  let index = 0;
  const automaticAt = '2026-10-02T08:00:00Z';
  const states = [{ studentUserId: 1, attendanceZone: 'Asia/Kolkata',
    totals: { presentDays: 1, absentDays: 1, totalDays: 2, percentage: 50 }, records: [
      { id: 1, serverSubmittedAt: timestamp, attendanceDate: '2026-10-01', attendanceResult: 'PRESENT', submissionType: 'PHYSICAL_CAPTURE', livenessStatus: 'VERIFIED' },
      { id: 2, serverSubmittedAt: automaticAt, attendanceDate: '2026-10-02', attendanceResult: 'ABSENT', submissionType: 'AUTO_ABSENT', livenessStatus: 'NOT_APPLICABLE' },
    ] }, '', 0];
  const wrap = ({ children }) => children;
  const { StudentAttendanceHistory } = load('components/AttendanceAbsenceOverview.tsx', {
    ...imports, react: { useEffect() {}, useState: () => [states[index++], () => {}] }, 'next/link': { default: wrap },
    '@/lib/api': {}, '@/lib/attendance': load('lib/attendance.ts'),
    '@/components/MentorRecordDelete': { MentorRecordDelete: () => null },
  });
  const html = renderToStaticMarkup(React.createElement(StudentAttendanceHistory, { role: 'MENTOR', studentId: 1, name: 'Assigned student', onBack() {} }));
  const date = value => new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Kolkata' }).format(new Date(value));
  assert.ok(html.includes(date(timestamp)));
  assert.ok(!html.includes(date(automaticAt)));
  assert.match(html, /automatic absence/);
});

test('admin directory keeps profile and onboarding dates independent with missing-record fallbacks', () => {
  let index = 0;
  const filters = { search: '' };
  const states = [{}, '', null, { totalPages: 1, content: [
    { id: 1, studentName: 'Submitted Student', profileLastStudentUpdatedAt: timestamp, onboardingLastStudentUpdatedAt: '2026-10-02T08:00:00Z' },
    { id: 2, studentName: 'No submissions', profileLastStudentUpdatedAt: null, onboardingLastStudentUpdatedAt: null },
  ] }, false, '', [], filters, filters];
  const wrap = ({ children }) => children;
  const { default: StudentsPage } = load('app/admin/students/page.tsx', {
    ...imports, react: { ...React, useEffect() {}, useCallback: value => value, useState: () => [states[index++], () => {}] },
    'next/navigation': { useSearchParams: () => new URLSearchParams() }, 'next/link': { default: wrap },
    '@/lib/api': {}, '@/components/AppFrame': { AppFrame: wrap }, '@/components/ProtectedRoute': { ProtectedRoute: wrap },
    '@/components/AttendanceAbsenceOverview': { StudentAttendanceHistory: () => null, formatAttendancePercentage: () => '?' },
    '@/components/ResetPasswordButton': { ResetPasswordButton: () => null },
    '@/components/StatusBadge': { StatusBadge: () => null }, '@/components/WorkflowBadge': { WorkflowBadge: () => null },
  });
  const html = renderToStaticMarkup(React.createElement(StudentsPage));
  assert.ok(html.includes(`dateTime="${timestamp}"`));
  assert.match(html, /dateTime="2026-10-02T08:00:00Z"/i);
  assert.equal((html.match(/Last updated by student:/g) || []).length, 4);
  assert.equal((html.match(/Not updated yet/g) || []).length, 2);
});
