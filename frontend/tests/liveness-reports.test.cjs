const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs"), path = require("node:path"), ts = require("typescript"), React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");

function harness(role, { mode = "attendance-report", type = "ATTENDANCE_SUMMARY", rows = [{ studentName: "Assigned Student", percentage: 75 }], api = {} } = {}) {
  const fields = [{ id: "studentName", label: "Student" }, { id: "percentage", label: "Present / Stored Records (%)" }];
  const reportTypes = ["ATTENDANCE", "ATTENDANCE_SUMMARY", "ATTENDANCE_DAILY_SUMMARY", "OVERALL", "MONTHLY_PROGRESS"].map(id => ({ id, title: id, available: true, fields }));
  const config = () => JSON.stringify({ reportType: slots[1], fields: slots[4], filters: slots[2], studentIds: slots[3] ? slots[3].split(",").map(Number) : [], page: slots[6], format: "XLSX" });
  const slots = { 0: { types: reportTypes, filters: [], date: "2026-11-02", scope: "Only authorized students" }, 1: type, 2: {}, 3: "", 4: fields.map(f => f.id), 5: { fields, rows, totalRows: rows.length, summary: { records: 4, present: 3, absent: 1, verified: 2, unverified: 1, historical: 1, notPerformed: 0, presentRecordPercentage: 75 } }, 6: 0 };
  slots[11] = config();
  let cursor = 0;
  const hooks = { useEffect() {}, useState(initial) { const i = cursor++; if (!(i in slots)) slots[i] = initial; return [slots[i], value => { slots[i] = typeof value === "function" ? value(slots[i]) : value; }]; } };
  function load(file, imports = {}) {
    const module = { exports: {} };
    const source = ts.transpileModule(fs.readFileSync(path.join(__dirname, "..", file), "utf8"), { compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText;
    new Function("require", "module", "exports", source)(name => imports[name] || require(name), module, module.exports);
    return module.exports;
  }
  const { ReportsCenter } = load("components/ReportsCenter.tsx", { react: hooks, "next/link": { default: ({ children, ...props }) => React.createElement("a", props, children) }, "@/lib/api": { api }, "@/lib/monthly-progress": { monthlyStatusLabel: value => value, formatDateTime: value => value }, "@/components/DashboardLayout": load("components/DashboardLayout.tsx"), "@/components/DashboardReportExport": { DashboardReportExport: () => null } });
  const render = () => { cursor = 0; return ReportsCenter({ role, mode }); };
  const nodes = tree => !tree || typeof tree !== "object" ? [] : [tree, ...React.Children.toArray(tree.props?.children).flatMap(nodes)];
  return { slots, config, html: () => renderToStaticMarkup(render()), nodes: () => nodes(render()) };
}

for (const role of ["ADMIN", "MENTOR"]) {
  test(`${role} Attendance Report shows only summaries, meaningful percentages and liveness statistics`, () => {
    const h = harness(role), html = h.html();
    assert.match(html, /Attendance Report/);
    assert.match(html, /ATTENDANCE_SUMMARY/); assert.match(html, /ATTENDANCE_DAILY_SUMMARY/);
    assert.doesNotMatch(html, />ATTENDANCE<|>OVERALL<|MONTHLY_PROGRESS|Actions|Delete|Approve|Reject|GPS|maps[?]/);
    assert.match(html, /Stored attendance records/); assert.match(html, /Present \/ stored records \(%\)/);
    assert.match(html, /Liveness verified/); assert.match(html, /Historical attendance/); assert.match(html, /75%/);
    assert.match(html, new RegExp(`href="/${role.toLowerCase()}/attendance"`));
  });

  test(`${role} general Reports Center links to Attendance Report and omits attendance report types`, () => {
    const html = harness(role, { mode: "general", type: "OVERALL" }).html();
    assert.match(html, /Reports Center/); assert.match(html, />OVERALL</); assert.match(html, /MONTHLY_PROGRESS/);
    assert.doesNotMatch(html, /ATTENDANCE_SUMMARY|ATTENDANCE_DAILY_SUMMARY|>ATTENDANCE</);
    assert.match(html, new RegExp(`href="/${role.toLowerCase()}/attendance-report"`));
  });

  test(`${role} stale raw attendance state never renders individual evidence or management actions in reports`, () => {
    const html = harness(role, { type: "ATTENDANCE", rows: [{ studentName: "RAW_ATTENDANCE_RECORD", attendanceLatitude: 18.52, recordId: 42 }] }).html();
    assert.doesNotMatch(html, /RAW_ATTENDANCE_RECORD|maps[?]|Actions|Delete|Approve|Reject/);
  });

  test(`${role} summary switch retains independent report filters and resets pagination`, () => {
    const h = harness(role); h.slots[2] = { historyFrom: "2026-10-01", historyTo: "2026-10-31" }; h.slots[3] = "42"; h.slots[6] = 3;
    const typeButton = h.nodes().find(n => n.type === "button" && React.Children.toArray(n.props.children)[0]?.props?.children === "ATTENDANCE_DAILY_SUMMARY");
    typeButton.props.onClick();
    assert.equal(h.slots[1], "ATTENDANCE_DAILY_SUMMARY"); assert.equal(h.slots[6], 0);
    assert.deepEqual(h.slots[2], { historyFrom: "2026-10-01", historyTo: "2026-10-31" }); assert.equal(h.slots[3], "42");
    assert.equal(h.slots[5], null);
  });
}
