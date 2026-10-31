const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), ts = require('typescript'), React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');
function load(file, imports = {}) {
  const m = { exports: {} };
  new Function('require', 'module', 'exports', ts.transpileModule(fs.readFileSync(path.join(__dirname, '..', file), 'utf8'), {
    compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
  }).outputText)(name => imports[name] || require(name), m, m.exports);
  return m.exports;
}
const lib = load('lib/internship-completion.ts');
const link = { default: ({ children, ...props }) => React.createElement('a', props, children) };
const dates = { formatDateTime: v => v || 'Not available' };
const flush = () => new Promise(resolve => setImmediate(resolve));
const nodes = tree => !tree || typeof tree !== 'object' ? [] : [tree, ...React.Children.toArray(tree.props?.children).flatMap(nodes)];
const completion = (eligible = true) => ({ internshipId: 9, studentId: 7, studentName: 'Existing Student', uid: 'UID7', companyName: 'Existing Company', startDate: '2025-01-01', endDate: '2025-01-09', totalWeeks: 2, sessionId: 4, sessionName: '2025-2026', status: 'COMPLETION_PENDING', revision: 2, completedAt: null, reviewerId: null, reviewer: null, remarks: null, requiredReports: 3, submittedReports: 3, pendingReports: 0, approvedReports: eligible ? 3 : 1, weeklyRequired: 2, monthlyRequired: 1, mentorApprovalStatus: eligible ? 'APPROVED' : 'PENDING', finalProjectReportStatus: 'NOT_APPLICABLE', eligible, blockers: eligible ? [] : ['2 reports require mentor approval'], history: [] });
function hooks(seed = {}, effects = []) {
  const slots = { ...seed }; let cursor = 0;
  return { slots, reset() { cursor = 0; }, react: { useEffect(cb) { effects.push(cb); }, useRef() { return { current: null }; }, useState(value) { const i = cursor++; if (!(i in slots)) slots[i] = typeof value === 'function' ? value() : value; return [slots[i], next => { slots[i] = typeof next === 'function' ? next(slots[i]) : next; }]; } } };
}
function panel(data, post) {
  const h = hooks({ 0: data });
  const { InternshipCompletionPanel } = load('components/InternshipCompletionPanel.tsx', { react: h.react, '@/lib/api': { api: { post } }, '@/lib/internship-completion': lib, '@/lib/monthly-progress': dates });
  const render = () => { h.reset(); return InternshipCompletionPanel({ studentId: 7 }); };
  return { ...h, render, button: text => nodes(render()).find(n => n.type === 'button' && n.props.children === text) };
}
test('completion controls remain disabled until all backend requirements are satisfied', () => {
  const h = panel(completion(false));
  assert.equal(h.button('Mark Internship as Completed').props.disabled, true);
  const html = renderToStaticMarkup(h.render()); assert.match(html, /2 reports require mentor approval/); assert.match(html, /2 weekly \+ 1 monthly/); assert.match(html, /Final project report/);
});
test('completion opens a confirmation and submits the current revision without forged status or reviewer', async () => {
  let request; const data = completion();
  const h = panel(data, async (url, body) => { request = { url, body }; return { data: { ...data, status: 'COMPLETED', revision: 3, completedAt: '2026-10-30T10:00:00Z', reviewer: 'iiic@test.edu' } }; });
  h.button('Mark Internship as Completed').props.onClick();
  const confirm = nodes(h.render()).find(n => typeof n.type === 'function' && n.props.onConfirm);
  assert.ok(confirm); assert.equal(confirm.props.reopening, false); assert.equal(confirm.props.completion.studentName, 'Existing Student');
  await confirm.props.onConfirm('All reviewed');
  assert.deepEqual(request, { url: '/admin/students/7/internship-completion/complete', body: { revision: 2, remarks: 'All reviewed' } });
  assert.equal(h.slots[0].status, 'COMPLETED'); assert.match(renderToStaticMarkup(h.render()), /student has been notified/);
});
test('reopening uses a separate confirmation and preserves the record after an API conflict', async () => {
  let request; const h = panel({ ...completion(), status: 'COMPLETED' }, async (url, body) => { request = { url, body }; throw { response: { data: { message: 'The completion record changed. Refresh.' } } }; });
  h.button('Reopen Internship').props.onClick();
  const confirm = nodes(h.render()).find(n => typeof n.type === 'function' && n.props.onConfirm); assert.equal(confirm.props.reopening, true);
  await confirm.props.onConfirm('Correction required');
  assert.equal(request.url, '/admin/students/7/internship-completion/reopen'); assert.equal(h.slots[0].status, 'COMPLETED'); assert.match(renderToStaticMarkup(h.render()), /record changed/);
});
test('student dashboard displays official completion date and reopening feedback', () => {
  const h = hooks({ 0: { ...completion(), status: 'COMPLETED', completedAt: '2026-10-30T10:00:00Z' } });
  const { StudentCompletionStatus } = load('components/InternshipCompletionPanel.tsx', { react: h.react, '@/lib/api': {}, '@/lib/internship-completion': lib, '@/lib/monthly-progress': dates });
  assert.match(renderToStaticMarkup(StudentCompletionStatus()), /Officially completed on 2026-10-30/);
  h.slots[0] = { ...completion(), history: [{ finalStatus: 'IN_PROGRESS', remarks: 'Review final work' }] }; h.reset();
  assert.match(renderToStaticMarkup(StudentCompletionStatus()), /Reopened: Review final work/);
});
test('clickable completion card links to the selected session and safely displays zero percent', () => {
  const { CompletionStatistics } = load('components/CompletionStatistics.tsx', { 'next/link': link });
  const html = renderToStaticMarkup(CompletionStatistics({ sessionId: 12, stats: { totalStudents: 2, totalInternships: 0, completedInternships: 0, activeInternships: 0, completionPending: 0, completionPercentage: 0 } }));
  assert.match(html, /sessionId=12/); assert.match(html, /0%/); assert.match(html, /Internships Completed/);
});
test('session hook uses the database active session, accepts known historical selection and ignores guessed IDs', async () => {
  const original = global.window;
  try {
    for (const [query, expected] of [['', 7], ['?sessionId=4', 4], ['?sessionId=999', 7]]) {
      global.window = { location: { search: query } }; const effects = [], h = hooks({}, effects);
      const { useAcademicSessions } = load('lib/academic-sessions.ts', { react: h.react, '@/lib/api': { api: { get: async url => { assert.equal(url, '/admin/academic-sessions'); return { data: [{ id: 4, active: false }, { id: 7, active: true }] }; } } } });
      useAcademicSessions(); effects[0](); await flush(); h.reset(); assert.equal(useAcademicSessions().selected, expected);
    }
  } finally { global.window = original; }
});
test('completion exports carry the selected session, status and the same company search', () => {
  assert.deepEqual(lib.completionReportFilters(4, 'COMPLETED', ' Acme '), { academicSessionId: '4', completionStatus: 'COMPLETED', completionSearch: 'Acme' });
  assert.deepEqual(lib.completionReportFilters(7, '', ''), { academicSessionId: '7', completionStatus: 'ALL' });
  assert.ok(lib.completionReportFields.includes('completionReviewer')); assert.ok(lib.completionReportFields.includes('completionDate'));
});
test('session settings show active and historical counts and offer historical calendar setup', () => {
  const h = hooks({ 0: [{ id: 4, name: '2026-2027', active: true, startDate: '2026-07-01', endDate: '2027-06-30', studentCount: 5 }, { id: 2, name: '2025-2026', active: false, startDate: null, endDate: null, studentCount: 8 }], 1: false });
  const pass = ({ children }) => children;
  const Page = load('app/admin/settings/academic-sessions/page.tsx', { react: h.react, '@/lib/api': {}, '@/components/AcademicSessionDeletionDialog': { SessionDeletionDialog: pass }, '@/components/AppFrame': { AppFrame: pass }, '@/components/ProtectedRoute': { ProtectedRoute: pass } }).default;
  const html = renderToStaticMarkup(Page()); assert.match(html, /Current active academic session/); assert.match(html, /2026-2027/); assert.match(html, /Set Dates/); assert.match(html, /Start New Academic Session/);
});
