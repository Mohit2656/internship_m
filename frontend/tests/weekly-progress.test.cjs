const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), ts = require('typescript'), React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');
function load(file, imports = {}) {
  const m = { exports: {} };
  new Function('require', 'module', 'exports', ts.transpileModule(fs.readFileSync(path.join(__dirname, '..', file), 'utf8'), {
    compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
  }).outputText)(n => imports[n] || require(n), m, m.exports);
  return m.exports;
}
const internship = load('lib/internship-details.ts');
const weekly = load('lib/weekly-progress.ts');
const dates = load('lib/monthly-progress.ts', { '@/lib/api': {} });
const flush = () => new Promise(resolve => setImmediate(resolve));
function elements(tree) { return !tree || typeof tree !== 'object' ? [] : [tree, ...React.Children.toArray(tree.props?.children).flatMap(elements)]; }
const week = (status = 'AVAILABLE') => ({ id: 12, internshipId: 2, studentUserId: 7, studentName: 'Assigned Student', uid: 'UID7',
  companyName: 'Existing Company', weekNumber: 1, periodStart: '2026-10-01', periodEnd: '2026-10-07', active: true,
  status, submissionStatus: status === 'AVAILABLE' ? 'NOT_SUBMITTED' : 'SUBMITTED', revision: status === 'AVAILABLE' ? 0 : 1,
  workDescription: status === 'AVAILABLE' ? null : 'Original work', submittedAt: null, reviewedAt: null,
  rejectionReason: status === 'REJECTED' ? 'Explain outcome' : null, attendanceSubmitted: 4, attendancePresent: 4, attendanceGpsVerified: 3, history: [] });
function harness(scope, rows, post) {
  const slots = { 0: rows, 2: false }; let cursor = 0;
  const hooks = { useEffect() {}, useState(value) { const i = cursor++; if (!(i in slots)) slots[i] = value; return [slots[i], next => { slots[i] = typeof next === 'function' ? next(slots[i]) : next; }]; } };
  const { WeeklyProgress } = load('components/WeeklyProgress.tsx', { react: hooks, '@/lib/api': { api: { post } },
    '@/lib/monthly-progress': dates, '@/lib/weekly-progress': weekly,
    'next/link': { default: ({ children, ...props }) => React.createElement('a', props, children) } });
  const render = () => { cursor = 0; return WeeklyProgress({ scope }); };
  return { slots, render, nodes: type => elements(render()).filter(n => n.type === type),
    button: text => elements(render()).find(n => n.type === 'button' && n.props.children === text), html: () => renderToStaticMarkup(render()) };
}

test('inclusive duration handles partial weeks, leap dates and invalid ranges', () => {
  assert.equal(internship.internshipWeeks('2026-11-01', '2026-12-30'), 9);
  assert.equal(internship.internshipWeeks('2026-11-01', '2026-11-07'), 1);
  assert.equal(internship.internshipWeeks('2026-11-01', '2026-11-08'), 2);
  assert.equal(internship.internshipWeeks('2024-02-27', '2024-03-04'), 1);
  for (const [start, end] of [['2026-11-01', '2026-11-01'], ['2026-11-08', '2026-11-01'], ['', '2026-11-01'], ['2026-02-30', '2026-03-04']]) assert.equal(internship.internshipWeeks(start, end), null);
});
test('form immediately recalculates readonly duration after either date changes and blocks invalid dates', () => {
  let cursor = 0, slots = {}, submitted;
  const { InternshipDetailsForm } = load('components/InternshipDetailsForm.tsx', { react: { useState(value) { const i = cursor++; if (!(i in slots)) slots[i] = value; return [slots[i], next => { slots[i] = typeof next === 'function' ? next(slots[i]) : next; }]; } }, '@/lib/internship-details': internship, '@/components/OpenStreetMapWorkplacePicker': { OpenStreetMapWorkplacePicker: () => null } });
  const render = () => { cursor = 0; return InternshipDetailsForm({ initial: { ...internship.emptyInternshipDetails, internshipMode: 'ONLINE', startDate: '2026-11-01', endDate: '2026-12-30', totalWeeks: '999' }, saving: false, fieldErrors: {}, onSubmit: data => { submitted = data; } }); };
  let tree = render();
  let input = elements(tree).find(n => n.props?.['aria-label'] === 'Calculated internship weeks');
  assert.equal(input.props.readOnly, true); assert.equal(input.props.value, 9);
  elements(tree).filter(n => n.type === 'input' && n.props.type === 'date')[1].props.onChange({ target: { value: '2026-11-07' } });
  tree = render(); assert.equal(elements(tree).find(n => n.props?.['aria-label'] === 'Calculated internship weeks').props.value, 1);
  tree.props.onSubmit({ preventDefault() {} }); assert.equal(submitted.totalWeeks, '1');
  elements(tree).filter(n => n.type === 'input' && n.props.type === 'date')[0].props.onChange({ target: { value: '2026-11-08' } });
  submitted = null; tree = render(); tree.props.onSubmit({ preventDefault() {} }); assert.equal(submitted, null);
  assert.match(renderToStaticMarkup(tree), /End date must be after start date/);
});
test('student sees periods, truthful attendance summaries, and submits a report for review', async () => {
  let request; const h = harness('student', [week()], async (url, body) => { request = { url, body }; return { data: { ...week('PENDING_MENTOR_REVIEW'), workDescription: body.workDescription } }; });
  assert.match(h.html(), /4 submitted · 4 present · 3 GPS verified/);
  h.button('Submit Weekly Report').props.onClick();
  h.nodes('textarea')[0].props.onChange({ target: { value: '  Built reporting endpoints  ' } });
  h.nodes('form')[0].props.onSubmit({ preventDefault() {} }); await flush();
  assert.deepEqual(request, { url: '/student/weekly-progress/12', body: { workDescription: 'Built reporting endpoints' } });
  assert.equal(h.slots[0][0].status, 'PENDING_MENTOR_REVIEW'); assert.match(h.html(), /submitted to your faculty mentor for review/);
});
test('rejected weekly corrections send the server revision and preserve editable work on errors', async () => {
  let request; const h = harness('student', [week('REJECTED')], async (url, body) => { request = { url, body }; throw { response: { data: { message: 'Revision changed' } } }; });
  h.button('Correct and Resubmit').props.onClick();
  assert.equal(h.nodes('textarea')[0].props.value, 'Original work');
  h.nodes('form')[0].props.onSubmit({ preventDefault() {} }); await flush();
  assert.deepEqual(request.body, { workDescription: 'Original work', revision: 1 });
  assert.equal(h.slots[0][0].status, 'REJECTED'); assert.match(h.html(), /Revision changed/); assert.ok(h.nodes('form').length);
});
test('mentor review requires rejection reason, submits revision, and handles approval', async () => {
  let calls = []; const h = harness('mentor', [week('PENDING_MENTOR_REVIEW')], async (url, body) => { calls.push({ url, body }); return { data: week(url.endsWith('reject') ? 'REJECTED' : 'APPROVED') }; });
  const grouping = h.nodes('details')[0]; assert.equal(grouping.props.open, undefined);
  h.button('Reject').props.onClick(); assert.equal(h.button('Confirm Rejection').props.disabled, true);
  h.nodes('form')[0].props.onSubmit({ preventDefault() {} }); await flush(); assert.equal(calls.length, 0);
  h.nodes('textarea')[0].props.onChange({ target: { value: 'Explain outcome' } });
  h.nodes('form')[0].props.onSubmit({ preventDefault() {} }); await flush();
  assert.deepEqual(calls[0], { url: '/mentor/weekly-progress/12/reject', body: { revision: 1, reason: 'Explain outcome' } });
  const approval = harness('mentor', [week('PENDING_MENTOR_REVIEW')], async (url, body) => { assert.deepEqual(body, { revision: 1 }); return { data: week('APPROVED') }; });
  approval.button('Approve').props.onClick(); await flush(); assert.equal(approval.slots[0][0].status, 'APPROVED'); assert.equal(approval.button('Approve'), undefined);
});
test('admin monitors without review actions; upcoming and archived weeks cannot be submitted', () => {
  const admin = harness('admin', [week('PENDING_MENTOR_REVIEW')]); assert.equal(admin.button('Approve'), undefined); assert.equal(admin.button('Reject'), undefined);
  for (const status of ['UPCOMING', 'ARCHIVED', 'APPROVED', 'PENDING_MENTOR_REVIEW']) assert.equal(harness('student', [{ ...week(status), active: status !== 'ARCHIVED' }]).button('Submit Weekly Report'), undefined);
  assert.deepEqual(weekly.groupWeeklyStudents([week(), { ...week(), id: 13 }, { ...week(), id: 14, studentUserId: 8 }]).map(([id, rows]) => [id, rows.length]), [[7, 2], [8, 1]]);
});
test('legacy date-review warnings identify the student while other weekly records remain available', () => {
  const h = harness('admin', [week()]);
  h.slots[11] = [{ internshipId: 99, studentUserId: 9, studentName: 'Legacy Student', message: 'Weekly records need an internship date review: duration exceeds 520 weeks. Existing internship records are preserved.' }];
  assert.match(h.html(), /Legacy Student/); assert.match(h.html(), /520 weeks/); assert.match(h.html(), /Existing Company/);
  assert.match(h.html(), /href="\/admin\/students\/9"/);
});
