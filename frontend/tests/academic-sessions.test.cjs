const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), ts = require('typescript'), React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');
function load(imports, file = 'app/admin/settings/academic-sessions/page.tsx') {
  const m = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname, '..', file), 'utf8');
  new Function('require', 'module', 'exports', ts.transpileModule(source, { compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText)(name => imports[name] || require(name), m, m.exports);
  return m.exports;
}
const flush = () => new Promise(resolve => setImmediate(resolve));
const nodes = tree => !tree || typeof tree !== 'object' ? [] : [tree, ...React.Children.toArray(tree.props?.children).flatMap(nodes)];
const active = { id: 4, name: '2026-2027', startDate: '2026-07-01', endDate: '2027-06-30', active: true, status: 'ACTIVE', studentCount: 5, closedAt: null };
const closed = { ...active, id: 2, name: '2025-2026', active: false, status: 'CLOSED', closedAt: '2026-10-30T10:00:00Z', closedBy: 'iiic@test.edu', studentCount: 8 };
const empty = { ...active, id: 9, name: '2027-2028', active: false, status: 'INACTIVE', studentCount: 0 };
const preview = { session: empty, records: { Internships: 0, 'Attendance records': 0, 'Monthly reports': 0, 'Completion history': 0 }, recordCount: 0, canDelete: true, explanation: 'Only this empty session will be permanently deleted. Its audit history is retained. This action cannot be undone.' };
function hooks(seed = {}) {
  const slots = { ...seed }; let cursor = 0;
  return { slots, reset() { cursor = 0; }, react: { useEffect() {}, useState(initial) { const i = cursor++; if (!(i in slots)) slots[i] = initial; return [slots[i], next => { slots[i] = typeof next === 'function' ? next(slots[i]) : next; }]; }, useRef(initial) { const i = cursor++; if (!(i in slots)) slots[i] = { current: initial }; return slots[i]; } } };
}
function page(api = {}) {
  const h = hooks({ 0: [active, closed, empty], 1: false }); const pass = ({ children }) => children;
  const dialogModule = load({ react: h.react }, 'components/AcademicSessionDeletionDialog.tsx');
  const mod = load({ react: h.react, '@/lib/api': { api }, '@/components/AcademicSessionDeletionDialog': dialogModule, '@/components/AppFrame': { AppFrame: pass }, '@/components/ProtectedRoute': { ProtectedRoute: pass } });
  const render = () => { h.reset(); return mod.default(); };
  return { ...h, render, button: label => nodes(render()).find(n => n.type === 'button' && n.props.children === label), dialog: () => nodes(render()).find(n => n.type === dialogModule.SessionDeletionDialog) };
}
function confirmation(props = {}) {
  const h = hooks(); const mod = load({ react: h.react }, 'components/AcademicSessionDeletionDialog.tsx'); let confirmations = 0;
  const render = () => { h.reset(); return mod.SessionDeletionDialog({ session: empty, preview, busy: false, error: '', onClose() {}, onConfirm() { confirmations++; }, ...props }); };
  return { render, get confirmations() { return confirmations; }, button: () => nodes(render()).find(n => n.type === 'button' && n.props.children === 'Permanently Delete Session'), typeName(value) { nodes(render()).find(n => n.type === 'input').props.onChange({ target: { value } }); }, submit() { nodes(render()).find(n => n.type === 'form').props.onSubmit({ preventDefault() {} }); } };
}
test('session table separates dates, displays ACTIVE/CLOSED/INACTIVE, and protects active deletion', () => {
  const h = page(); const html = renderToStaticMarkup(h.render());
  for (const label of ['Start Date', 'End Date', 'Students', 'Actions', 'ACTIVE', 'CLOSED', 'INACTIVE', 'Reopen and Activate', 'iiic@test.edu']) assert.ok(html.includes(label));
  const deletes = nodes(h.render()).filter(n => n.type === 'button' && n.props.children === 'Delete Session');
  assert.equal(deletes[0].props.disabled, true); assert.equal(deletes[1].props.disabled, false);
  assert.match(html, /academicSessionId=2/);
});
test('close requires coordinator confirmation with session identity and preserves the row', async t => {
  const previous = global.window; t.after(() => { global.window = previous; }); let accept = false, message = ''; const requests = [];
  global.window = { confirm(text) { message = text; return accept; } };
  const h = page({ post: async (url, data) => { requests.push({ url, data }); }, get: async () => ({ data: [{ ...active, status: 'CLOSED', active: false }] }) });
  h.button('Close Session').props.onClick(); await flush(); assert.equal(requests.length, 0); assert.match(message, /2026-2027/); assert.match(message, /preserved/);
  accept = true; h.button('Close Session').props.onClick(); await flush();
  assert.deepEqual(requests, [{ url: '/admin/academic-sessions/4/close', data: { confirmed: true, sessionName: '2026-2027' } }]);
  assert.match(renderToStaticMarkup(h.render()), /CLOSED/);
});
test('closed sessions use an explicit reopen operation and explain automatic closure of the current session', async t => {
  const previous = global.window; t.after(() => { global.window = previous; }); let message = '', request;
  global.window = { confirm(text) { message = text; return true; } };
  const h = page({ post: async (url, data) => { request = { url, data }; }, get: async () => ({ data: [active, closed, empty] }) });
  h.button('Reopen and Activate').props.onClick(); await flush();
  assert.match(message, /Explicitly reopen and activate 2025-2026/); assert.match(message, /current active session will be closed/);
  assert.equal(request.url, '/admin/academic-sessions/2/reopen'); assert.equal(request.data.confirmed, true);
});
test('empty inactive activation remains available and closes the previous session', async t => {
  const previous = global.window; t.after(() => { global.window = previous; }); let message = '', url;
  global.window = { confirm(text) { message = text; return true; } };
  const h = page({ post: async value => { url = value; }, get: async () => ({ data: [active, closed, empty] }) });
  h.button('Activate').props.onClick(); await flush(); assert.equal(url, '/admin/academic-sessions/9/activate'); assert.match(message, /current active session will be closed/);
});
test('permanent deletion requires an exact typed name and a successful dependency review', () => {
  const h = confirmation(); const html = renderToStaticMarkup(h.render());
  assert.match(html, /2027-2028/); assert.match(html, /Student accounts \(including inactive\)/); assert.match(html, /Attendance records/); assert.match(html, /cannot be undone/);
  assert.equal(h.button().props.disabled, true);h.typeName('wrong');h.submit();assert.equal(h.confirmations, 0);
  h.typeName(empty.name);assert.equal(h.button().props.disabled, false);h.submit();assert.equal(h.confirmations, 1);
  for (const p of [null, { ...preview, canDelete: false }]) { const blocked = confirmation({ preview: p });blocked.submit();assert.equal(blocked.button().props.disabled, true);assert.equal(blocked.confirmations, 0); }
});
test('sessions with historical data show counts, preserved history, and a disabled permanent delete action', () => {
  const h = confirmation({ session: closed, preview: { ...preview, session: closed, records: { Internships: 4, 'Attendance records': 120, 'Monthly reports': 8 }, canDelete: false, explanation: 'This session contains student accounts or historical records and cannot be permanently deleted.' } });
  const html = renderToStaticMarkup(h.render());assert.match(html, /120/);assert.match(html, /already safely closed/);assert.equal(h.button().props.disabled, true);h.submit();assert.equal(h.confirmations, 0);
});
test('deletion fetches dependencies before any mutation and submits only the confirmed session identity', async () => {
  const requests = [];const h = page({ get: async url => { requests.push(['get', url]);return { data: url.endsWith('deletion-preview') ? preview : [active, closed] }; }, delete: async (url, data) => { requests.push(['delete', url, data]); } });
  const deleteButtons = nodes(h.render()).filter(n => n.type === 'button' && n.props.children === 'Delete Session');deleteButtons[2].props.onClick();await flush();
  assert.deepEqual(requests, [['get', '/admin/academic-sessions/9/deletion-preview']]);assert.equal(h.dialog().props.preview.canDelete, true);
  h.dialog().props.onConfirm();await flush();
  assert.deepEqual(requests[1], ['delete', '/admin/academic-sessions/9', { data: { confirmed: true, sessionName: empty.name } }]);assert.equal(h.dialog(), undefined);assert.equal(h.slots[0].length, 2);
});
test('server dependency conflicts preserve the session and disable further deletion until a new review', async () => {
  const h = page({ get: async () => ({ data: preview }), delete: async () => { throw { response: { data: { message: 'A student was added. Close this session to preserve history.' } } }; } });
  nodes(h.render()).filter(n => n.type === 'button' && n.props.children === 'Delete Session')[2].props.onClick();await flush();h.dialog().props.onConfirm();await flush();
  assert.equal(h.slots[0].length, 3);assert.equal(h.dialog().props.preview, null);assert.match(h.dialog().props.error, /A student was added/);
});
