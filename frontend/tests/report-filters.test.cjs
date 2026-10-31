const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), ts = require('typescript'), React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');
const flush = () => new Promise(resolve => setImmediate(resolve));
const nodes = tree => !tree || typeof tree !== 'object' ? [] : [tree, ...React.Children.toArray(tree.props?.children).flatMap(nodes)];
const fields = [{ id: 'studentId', label: 'Student account ID' }, { id: 'uid', label: 'UID' }, { id: 'studentName', label: 'Student' }];
const selectors = { branch: ['COMPUTER_ENGINEERING', 'CIVIL'], academicSessionId: ['11', '12'], completionStatus: ['ALL', 'NOT_STARTED', 'IN_PROGRESS', 'COMPLETION_PENDING', 'COMPLETED'], semester: ['1', '8'], profileStatus: ['COMPLETE', 'INCOMPLETE'], workflowStatus: ['NOT_SECURED', 'INTERNSHIP_SECURED', 'DOCUMENTS_PENDING', 'DOCUMENTS_COMPLETE', 'DIARY_ISSUED', 'INTERNSHIP_DETAILS_COMPLETE'], internshipMode: ['ONLINE', 'HYBRID', 'OFFICE_REPORTING', 'COLLEGE_REPORTING'], internshipSource: ['DEPARTMENT', 'CDC', 'SELF'], attendanceStatus: ['PRESENT', 'ABSENT'], verificationStatus: ['VERIFIED', 'FAILED', 'HISTORICAL', 'NOT_PERFORMED', 'NOT_APPLICABLE'], mentorAssignment: ['ASSIGNED', 'UNASSIGNED'], companyType: ['IT', 'MANUFACTURING', 'CORE', 'OTHER'], paid: ['PAID', 'UNPAID'], fullTimeOffered: ['YES', 'NO'], accountActive: ['YES', 'NO'], monthlyStatus: ['PENDING_MENTOR_REVIEW', 'APPROVED', 'REJECTED'] };
const filters = ['search', 'studentId', 'branch', 'completionSearch', 'academicSessionId', 'completionStatus', 'academicYear', 'semester', 'profileStatus', 'workflowStatus', 'company', 'internshipMode', 'internshipSource', 'mentorId', 'mentorAssignment', 'companyType', 'paid', 'fullTimeOffered', 'accountActive', 'historyFrom', 'historyTo', 'attendanceStatus', 'verificationStatus', 'monthlyStatus'].map(id => ({ id, label: `Filter ${id}`, type: selectors[id] ? 'select' : ['historyFrom', 'historyTo'].includes(id) ? 'date' : ['studentId', 'mentorId'].includes(id) ? 'number' : 'text', choices: (selectors[id] || []).map(value => ({ value, label: id === 'academicSessionId' ? `TEST_REPORT_SESSION_${value}` : value })), description: '' }));
const options = { types: ['ROSTER', 'COMPLETIONS', 'MONTHLY_PROGRESS', 'ATTENDANCE', 'ATTENDANCE_SUMMARY', 'OVERALL', 'STIPEND', 'STIPEND_DISTRIBUTION', 'COMPANY', 'COMPANY_STIPEND', 'COMPARISON', 'PLACEMENT', 'PLACEMENT_COMPANY', 'WORKFLOW', 'DIARY_ISSUANCE'].map(id => ({ id, title: id, available: true, fields })), filters, date: '2026-10-30', scope: 'Local report fixture' };
function harness({ type = 'ROSTER', initialFilters = {}, ids = '', page = 0, table = null, api = {} } = {}) {
  const slots = { 0: options, 1: type, 2: initialFilters, 3: ids, 4: fields.map(f => f.id), 5: table, 6: page, 12: '' }; let cursor = 0, effect = 0; const effects = [];
  function config() { return JSON.stringify({ reportType: slots[1], fields: slots[4], filters: Object.fromEntries(Object.entries(slots[2]).filter(([, value]) => value.trim())), studentIds: slots[3].trim() ? slots[3].split(',').map(value => Number(value.trim())) : [], page: slots[6], format: 'XLSX' }); }
  if (table) slots[12] = config();
  const hooks = { useEffect(callback) { effects[effect++] = callback; }, useState(initial) { const i = cursor++; if (!(i in slots)) slots[i] = initial; return [slots[i], value => { slots[i] = typeof value === 'function' ? value(slots[i]) : value; }]; } };
  const stub = () => null, m = { exports: {} };
  const source = ts.transpileModule(fs.readFileSync(path.join(__dirname, '../components/ReportsCenter.tsx'), 'utf8'), { compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
  const imports = { react: hooks, '@/lib/api': { api }, '@/lib/attendance': { livenessLabel: value => value, locationVerificationLabel: value => value }, '@/lib/monthly-progress': { monthlyStatusLabel: value => value, formatDateTime: value => value }, '@/components/AttendanceLocations': { AttendanceLocations: stub }, '@/components/MentorRecordDelete': { MentorRecordDelete: stub }, '@/components/DashboardReportExport': { DashboardReportExport: stub } };
  new Function('require', 'module', 'exports', source)(name => imports[name] || require(name), m, m.exports);
  const render = () => { cursor = 0; effect = 0; return m.exports.ReportsCenter({ role: 'ADMIN' }); };
  const button = text => nodes(render()).find(n => n.type === 'button' && n.props.children === text);
  const filter = id => { const label = nodes(render()).find(n => n.type === 'label' && React.Children.toArray(n.props.children)[0] === `Filter ${id}`); return label && nodes(label).find(n => n.type === 'select' || n.type === 'input'); };
  return { slots, render, button, filter, effects, config, html: () => renderToStaticMarkup(render()) };
}
for (const type of options.types.map(report => report.id)) test(`${type}: every available filter control forwards each value, All clears it, and changes reset pagination`, () => {
  const h = harness({ type, page: 4 });
  for (const filter of filters) {
    const available = (type === 'MONTHLY_PROGRESS' || filter.id !== 'monthlyStatus') && (type.startsWith('ATTENDANCE') || !['historyFrom', 'historyTo', 'attendanceStatus', 'verificationStatus'].includes(filter.id));
    assert.equal(Boolean(h.filter(filter.id)), available, `${type} / ${filter.id}`); if (!available) continue;
    const values = selectors[filter.id] || [filter.type === 'date' ? '2026-10-29' : filter.type === 'number' ? '11' : 'TEST_REPORT_STUDENT_01'];
    for (const value of values) { h.slots[6] = 4; h.filter(filter.id).props.onChange({ target: { value } }); assert.equal(JSON.parse(h.config()).filters[filter.id], value); assert.equal(h.slots[6], 0); }
    h.filter(filter.id).props.onChange({ target: { value: '' } }); assert.equal(filter.id in JSON.parse(h.config()).filters, false);
  }
});
test('session dropdown uses real IDs and readable names instead of guessed year numbers', () => {
  const h = harness();const control = h.filter('academicSessionId');assert.equal(control.type, 'select');assert.match(h.html(), /TEST_REPORT_SESSION_11/);control.props.onChange({ target: { value: '12' } });assert.equal(JSON.parse(h.config()).filters.academicSessionId, '12');
});
test('clearing filters restores all students, clears explicit selection, resets pagination and invalidates the old preview', () => {
  const h = harness({ initialFilters: { company: 'ALPHA', completionStatus: 'COMPLETED' }, ids: '11, 12', page: 2, table: { fields, rows: [], totalRows: 1, summary: {} } });
  h.button('Clear filters').props.onClick();assert.deepEqual(JSON.parse(h.config()).filters, {});assert.deepEqual(JSON.parse(h.config()).studentIds, []);assert.equal(h.slots[6], 0);assert.equal(h.button('Export Excel').props.disabled, true);
});
test('pagination displays server totals and all 60 matching students across two pages', () => {
  const rows = Array.from({ length: 50 }, (_, index) => ({ studentId: index + 1, uid: `TEST_REPORT_UID_${index + 1}`, studentName: `TEST_REPORT_STUDENT_${index + 1}` }));
  const h = harness({ table: { fields, rows, totalRows: 60, summary: {} } });assert.match(h.html(), /60 records match/);assert.equal(h.button('Previous').props.disabled, true);assert.equal(h.button('Next').props.disabled, false);h.button('Next').props.onClick();assert.equal(h.slots[6], 1);
  h.slots[5] = { fields, rows: rows.slice(0, 10), totalRows: 60, summary: {} };h.slots[12] = h.config();assert.equal(h.button('Next').props.disabled, true);assert.equal(h.button('Previous').props.disabled, false);assert.equal(nodes(h.render()).filter(n => n.type === 'tbody')[0].props.children.length, 10);
});
test('an older aborted preview cannot overwrite newer filter results even if its response still arrives', async t => {
  const previous = global.window;t.after(() => { global.window = previous; });const timers = [];global.window = { setTimeout(callback) { timers.push(callback);return timers.length; }, clearTimeout() {} };
  const requests = [];const h = harness({ api: { post(url, body, config) { return new Promise(resolve => requests.push({ url, body, signal: config.signal, resolve })); } } });
  h.render();const cleanup = h.effects[3]();timers.shift()();h.filter('company').props.onChange({ target: { value: 'BETA' } });h.render();cleanup();h.effects[3]();timers.shift()();
  const current = { fields, rows: [{ studentId: 7, studentName: 'TEST_REPORT_STUDENT_07' }], totalRows: 1, summary: {} };requests[1].resolve({ data: current });await flush();assert.equal(h.slots[5], current);assert.equal(h.slots[12], h.config());assert.equal(requests[0].signal.aborted, true);
  requests[0].resolve({ data: { fields, rows: [], totalRows: 0, summary: {} } });await flush();assert.equal(h.slots[5], current);assert.equal(h.slots[12], h.config());assert.match(h.html(), /TEST_REPORT_STUDENT_07/);
});
