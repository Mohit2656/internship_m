const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), ts = require('typescript'), React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');
function load(file, imports = {}) {
  const m = { exports: {} };
  new Function('require', 'module', 'exports', ts.transpileModule(fs.readFileSync(path.join(__dirname, '..', file), 'utf8'), { compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText)(n => imports[n] || require(n), m, m.exports);
  return m.exports;
}
const student = { id: 7, studentName: 'Aditi Sharma', uid: 'UID7', loginEmail: 'aditi@college.edu' };
const preview = { studentId: 7, name: 'Aditi Sharma', uid: 'UID7', email: 'aditi@college.edu', active: true, dependencies: { 'Attendance and GPS evidence': 20, 'Weekly, monthly and project records': 9 }, applicationsNote: 'Stored matching history is retained.' };
const result = { studentId: 7, active: false, initiatedBy: 1, deactivatedAt: '2026-10-30T10:00:00Z', reason: 'Duplicate account' };
const flush = () => new Promise(resolve => setImmediate(resolve));
const nodes = tree => !tree || typeof tree !== 'object' ? [] : [tree, ...React.Children.toArray(tree.props?.children).flatMap(nodes)];
function harness({ role = 'ADMIN', userId = 1, get = async () => ({ data: preview }), remove = async () => ({ data: result }) } = {}) {
  const slots = []; let cursor = 0, changed = 0, deleted = null;
  const hooks = { useEffect() {}, useState(initial) { const i = cursor++; if (!(i in slots)) slots[i] = initial; return [slots[i], value => { slots[i] = typeof value === 'function' ? value(slots[i]) : value; }]; }, useRef(value) { const i = cursor++; if (!(i in slots)) slots[i] = { current: value }; return slots[i]; } };
  const { DeleteStudentButton } = load('components/DeleteStudentButton.tsx', { react: hooks, '@/lib/api': { api: { get, delete: remove } }, '@/lib/auth': { useAuth: () => ({ user: { role, id: userId } }) }, '@/lib/student-deletion': { publishStudentAccountsChanged() { changed++; } } });
  const render = () => { cursor = 0; return DeleteStudentButton({ student, onDeleted(data) { deleted = data; } }); };
  return { slots, render, nodes: () => nodes(render()), button: text => nodes(render()).find(n => n.type === 'button' && n.props.children === text), html: () => renderToStaticMarkup(render()), get changed() { return changed; }, get deleted() { return deleted; } };
}
async function open(h) { h.button('Delete Student').props.onClick(); await flush(); }
function confirm(h, reason = 'Duplicate account') { h.nodes().find(n => n.type === 'textarea').props.onChange({ target: { value: reason } }); h.nodes().find(n => n.type === 'input' && n.props.type === 'checkbox').props.onChange({ target: { checked: true } }); }
test('only IIIC administrators see delete and the current administrator is protected', () => {
  for (const role of ['STUDENT', 'MENTOR']) assert.equal(harness({ role }).render(), null);
  assert.equal(harness({ userId: 7 }).render(), null); assert.ok(harness().button('Delete Student'));
});
test('confirmation displays name UID email and preservation policy before any deletion', async () => {
  let calls = 0; const h = harness({ remove: async () => { calls++; } }); await open(h);
  assert.match(h.html(), /Aditi Sharma/); assert.match(h.html(), /UID7/); assert.match(h.html(), /aditi@college.edu/); assert.match(h.html(), /Historical attendance/); assert.match(h.html(), /Attendance and GPS evidence/);
  assert.equal(h.button('Confirm Delete Student').props.disabled, true); assert.equal(calls, 0);
});
test('explicit checkbox and valid reason are both required even for direct form submission', async () => {
  let calls = 0; const h = harness({ remove: async () => { calls++; return { data: result }; } }); await open(h);
  h.nodes().find(n => n.type === 'form').props.onSubmit({ preventDefault() {} }); await flush(); assert.equal(calls, 0);
  h.nodes().find(n => n.type === 'textarea').props.onChange({ target: { value: 'Duplicate account' } }); assert.equal(h.button('Confirm Delete Student').props.disabled, true);
  confirm(h, '   '); assert.equal(h.button('Confirm Delete Student').props.disabled, true); confirm(h); assert.equal(h.button('Confirm Delete Student').props.disabled, false);
});
test('cancel leaves the student untouched and closes the confirmation', async () => {
  let calls = 0; const h = harness({ remove: async () => { calls++; } }); await open(h); h.button('Cancel').props.onClick();
  assert.equal(h.nodes().some(n => n.type === 'dialog'), false); assert.equal(calls, 0);
});
test('confirmed deletion submits backend identity and reason and refreshes parent and dashboards once', async () => {
  let request; const h = harness({ remove: async (url, config) => { request = { url, config }; return { data: result }; } }); await open(h); confirm(h);
  h.nodes().find(n => n.type === 'form').props.onSubmit({ preventDefault() {} }); await flush();
  assert.deepEqual(request, { url: '/admin/students/7', config: { data: { confirmed: true, confirmationEmail: preview.email, reason: 'Duplicate account' } } });
  assert.equal(h.changed, 1); assert.deepEqual(h.deleted, result); assert.equal(h.nodes().some(n => n.type === 'dialog'), false);
});
test('pending deletion suppresses double submission and failures keep the dialog and account visible', async () => {
  let reject, calls = 0; const h = harness({ remove: () => { calls++; return new Promise((_, no) => { reject = no; }); } }); await open(h); confirm(h);
  const submit = () => h.nodes().find(n => n.type === 'form').props.onSubmit({ preventDefault() {} }); submit(); submit();
  assert.equal(calls, 1); assert.equal(h.button('Deleting…').props.disabled, true);
  reject({ response: { data: { message: 'Deletion was rolled back.' } } }); await flush();
  assert.match(h.html(), /Deletion was rolled back/); assert.equal(h.deleted, null); assert.equal(h.changed, 0); assert.ok(h.nodes().some(n => n.type === 'dialog'));
});
test('failed dependency inspection and already deactivated accounts cannot be confirmed', async () => {
  const failed = harness({ get: async () => { throw new Error('unavailable'); } }); await open(failed); confirm(failed);
  assert.equal(failed.button('Confirm Delete Student').props.disabled, true); assert.match(failed.html(), /No account has been changed/);
  const inactive = harness({ get: async () => ({ data: { ...preview, active: false } }) }); await open(inactive); confirm(inactive);
  assert.equal(inactive.button('Confirm Delete Student').props.disabled, true); assert.match(inactive.html(), /already deactivated/);
});
test('refresh event reaches same-tab dashboards and stores only a change marker for other tabs', () => {
  const previous = global.window; let event, stored;
  try { global.window = { dispatchEvent(value) { event = value.type; }, localStorage: { setItem(...args) { stored = args; } } }; const lib = load('lib/student-deletion.ts'); lib.publishStudentAccountsChanged(); assert.equal(event, lib.STUDENT_ACCOUNTS_CHANGED); assert.equal(stored[0], lib.STUDENT_ACCOUNTS_CHANGED); assert.doesNotMatch(stored[1], /aditi|UID7/); }
  finally { global.window = previous; }
});
test('dashboard refetches selected-session statistics after deletion and removes refresh listeners on unmount', async () => {
  const previous = global.window, listeners = new Map(), calls = [], slots = [], effects = []; let cursor = 0;
  const summary = { students: { total: 0, profileComplete: 0, profileIncomplete: 0 }, internshipProgress: {}, sources: [], companyTypes: [], internshipModes: [], stipend: {}, fullTimeEmployment: {}, topCompanies: [], attention: {} };
  const hooks = { useEffect(cb) { effects.push(cb); }, useState(value) { const i = cursor++; if (!(i in slots)) slots[i] = value; return [slots[i], next => { slots[i] = typeof next === 'function' ? next(slots[i]) : next; }]; } };
  const pass = () => null;
  const imports = { react: hooks, 'next/link': { default: pass }, '@/lib/api': { api: { get: async (url, config) => { calls.push({ url, sessionId: config.params.sessionId }); return { data: url.endsWith('/summary') ? summary : { sessionId: 4, stats: {}, content: [] } }; } } }, '@/lib/academic-sessions': { useAcademicSessions: () => ({ sessions: [], selected: 4, setSelected() {} }) }, '@/lib/student-deletion': { STUDENT_ACCOUNTS_CHANGED: 'ims:student-accounts-changed' } };
  for (const name of ['AppFrame', 'ProtectedRoute', 'DashboardReportExport', 'AttendanceAbsenceOverview', 'RecentNotifications', 'AcademicSessionPicker', 'CompletionStatistics']) imports[`@/components/${name === 'RecentNotifications' ? 'NotificationsContent' : name}`] = { [name]: pass };
  try {
    global.window = { addEventListener(type, fn) { listeners.set(type, fn); }, removeEventListener(type) { listeners.delete(type); } };
    const Page = load('app/admin/dashboard/page.tsx', imports).default;
    Page(); const cleanup = effects[0](); effects[1](); await flush();
    assert.equal(calls.length, 2); assert.ok(calls.every(call => call.sessionId === 4));
    listeners.get('ims:student-accounts-changed')(); assert.equal(slots[2], 1);
    cursor = 0; effects.length = 0; Page(); effects[1](); await flush(); assert.equal(calls.length, 4);
    listeners.get('storage')({ key: 'unrelated' }); assert.equal(slots[2], 1);
    listeners.get('storage')({ key: 'ims:student-accounts-changed' }); assert.equal(slots[2], 2);
    cleanup(); assert.equal(listeners.size, 0);
  } finally { global.window = previous; }
});
