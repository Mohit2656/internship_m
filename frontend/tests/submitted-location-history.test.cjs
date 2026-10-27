const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const ts = require("typescript");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");

require("./register-tsx.cjs");

function load(file, imports) {
  const module = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname, "..", file), "utf8");
  const output = ts.transpileModule(source, {
    compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  new Function("require", "module", "exports", output)(name => imports[name] || require(name), module, module.exports);
  return module.exports;
}

const totals = { presentDays: 2, absentDays: 0, totalDays: 2, percentage: 100 };
const entry = (id, evidence) => ({
  id, attendanceDate: `2026-10-${19 + id}`, attendanceResult: "PRESENT", submissionType: "PHYSICAL_CAPTURE",
  serverSubmittedAt: "2026-10-20T05:00:00Z", livenessStatus: "FAILED", evidence,
});
const history = (studentUserId, records) => ({ studentUserId, attendanceZone: "Asia/Kolkata", totals, records });

// Exercise the real history component's request/effect lifecycle while rendering the
// real location components. No location or workplace data is supplied by the harness.
function historyHarness(role, responses) {
  const states = [], effectStates = [], calls = [];
  let cursor, effectCursor, pending, dirty, tree;
  let props = { role, studentId: 42, name: "First student", onBack() {} };
  const hooks = {
    ...React,
    useState(initial) {
      const index = cursor++;
      if (!(index in states)) states[index] = initial;
      return [states[index], value => {
        const next = typeof value === "function" ? value(states[index]) : value;
        if (!Object.is(next, states[index])) { states[index] = next; dirty = true; }
      }];
    },
    useEffect(effect, deps) {
      const index = effectCursor++, previous = effectStates[index];
      if (!previous || deps.some((value, i) => !Object.is(value, previous.deps[i]))) {
        const state = { deps, cleanup: previous?.cleanup };
        effectStates[index] = state;
        pending.push(() => { state.cleanup?.(); state.cleanup = effect(); });
      }
    },
  };
  const { StudentAttendanceHistory } = load("components/AttendanceAbsenceOverview.tsx", {
    react: hooks,
    "@/lib/api": { api: { async get(url, { signal }) {
      calls.push(url);
      assert.equal(signal.aborted, false);
      return { data: responses[Number(url.split("/").at(-1))] };
    } } },
    "@/components/MentorRecordDelete": { MentorRecordDelete: () => null },
    "next/link": { default: ({ children, ...rest }) => React.createElement("a", rest, children) },
  });
  function draw() {
    let passes = 0;
    do {
      assert.ok(++passes < 10, "history effects should settle");
      cursor = effectCursor = 0; pending = []; dirty = false;
      tree = StudentAttendanceHistory(props);
      for (const effect of pending) effect();
    } while (dirty);
    return renderToStaticMarkup(tree);
  }
  return {
    calls,
    async flush() { draw(); await new Promise(resolve => setImmediate(resolve)); return draw(); },
    selectStudent(studentId) { props = { ...props, studentId, name: "Second student" }; },
    close() { for (const effect of effectStates) effect?.cleanup?.(); },
  };
}

function mapLinks(html) {
  return [...html.matchAll(/<a\b[^>]*href="([^"]+)"[^>]*>([^<]*)<\/a>/g)]
    .filter(([, href]) => href.startsWith("https://www.google.com/maps"))
    .map(([anchor, href, label]) => ({ anchor, url: new URL(href.replaceAll("&amp;", "&")), label }));
}

function assertSubmittedLinks(html, coordinates) {
  const links = mapLinks(html);
  assert.deepEqual(links.map(({ url }) => url.searchParams.get("q")), coordinates);
  for (const { anchor, url, label } of links) {
    assert.equal(url.origin, "https://www.google.com");
    assert.equal(url.pathname, "/maps");
    assert.equal(label, "View Submitted Location on Google Maps");
    assert.match(anchor, /target="_blank"/);
    assert.match(anchor, /rel="noopener noreferrer"/);
  }
}

for (const role of ["ADMIN", "MENTOR"]) {
  test(`${role} dashboard history links each saved attendance location and switches students independently`, async t => {
    const responses = {
      42: history(42, [
        entry(1, { latitude: 18.5204, longitude: 73.8567, gpsAccuracyMeters: 12, expectedLatitude: 21.125, expectedLongitude: 79.0875 }),
        entry(2, { latitude: -33.8688, longitude: 151.2093, gpsAccuracyMeters: 240, locationVerificationStatus: "OUTSIDE_RADIUS" }),
      ]),
      43: history(43, [entry(3, { latitude: 0, longitude: 0, expectedLatitude: 18.5204, expectedLongitude: 73.8567 })]),
    };
    const h = historyHarness(role, responses);
    t.after(() => h.close());
    const first = await h.flush();
    assertSubmittedLinks(first, ["18.5204,73.8567", "-33.8688,151.2093"]);
    assert.match(first, /Liveness not confirmed/);
    assert.match(first, /Reported GPS accuracy: 240 m/);
    assert.doesNotMatch(first, /q=21[.]125/);
    h.selectStudent(43);
    const second = await h.flush();
    assertSubmittedLinks(second, ["0,0"]);
    assert.doesNotMatch(second, /q=18[.]5204|q=-33[.]8688/);
    assert.deepEqual(h.calls, [`/${role.toLowerCase()}/attendance/students/42`, `/${role.toLowerCase()}/attendance/students/43`]);
  });

  test(`${role} dashboard history shows unavailable for missing or invalid submitted coordinates, even with a workplace`, async t => {
    const evidence = [
      undefined, null, {}, { latitude: null, longitude: null },
      { latitude: 18.52, longitude: null }, { longitude: 73.85 },
      { latitude: 91, longitude: 73 }, { latitude: 18, longitude: -181 },
      { latitude: NaN, longitude: 73 }, { latitude: 18, longitude: Infinity },
      { latitude: "18.52", longitude: 73.85 },
      { expectedLatitude: 21.125, expectedLongitude: 79.0875, workplaceAddress: "Registered workplace" },
    ];
    const h = historyHarness(role, { 42: history(42, evidence.map((value, index) => entry(index + 1, value))) });
    t.after(() => h.close());
    const html = await h.flush();
    assertSubmittedLinks(html, []);
    assert.equal((html.match(/Submission location unavailable for this attendance record[.]/g) || []).length, evidence.length);
    assert.doesNotMatch(html, /Registered workplace/);
  });
}

const attendance = require("../lib/attendance.ts");
const monthlyProgress = load("lib/monthly-progress.ts", { "@/lib/api": {} });

for (const role of ["ADMIN", "MENTOR"]) {
  test(`${role} attendance report preserves submitted map links for separate rows and unavailable historical data`, () => {
    const fields = [{ id: "attendanceLatitude", label: "Submission latitude" }, { id: "attendanceLongitude", label: "Submission longitude" }];
    const selected = fields.map(field => field.id);
    const config = JSON.stringify({ reportType: "ATTENDANCE", fields: role === "MENTOR" ? [...new Set([...selected, "recordId", "studentName", "uid", "date"])] : selected,
      filters: {}, studentIds: [], page: 0, format: "XLSX" });
    const options = { types: [{ id: "ATTENDANCE", title: "Attendance", available: true, fields }], filters: [] };
    const rows = [
      { attendanceLatitude: 18.5204, attendanceLongitude: 73.8567 },
      { attendanceLatitude: -33.8688, attendanceLongitude: 151.2093 },
      { attendanceLatitude: null, attendanceLongitude: null, expectedLatitude: 21.125, expectedLongitude: 79.0875 },
    ];
    const table = { fields, rows, summary: {}, totalRows: rows.length };
    const values = { 0: options, 1: "ATTENDANCE", 4: selected, 5: table, 12: config };
    let cursor = 0;
    const { ReportsCenter } = load("components/ReportsCenter.tsx", {
      react: { useEffect() {}, useState(initial) { const index = cursor++; return [index in values ? values[index] : initial, () => {}]; } },
      "@/lib/api": {}, "@/lib/attendance": attendance, "@/lib/monthly-progress": monthlyProgress,
      "@/components/MentorRecordDelete": { MentorRecordDelete: () => null },
      "@/components/DashboardReportExport": { DashboardReportExport: () => null },
    });
    const html = renderToStaticMarkup(React.createElement(ReportsCenter, { role, attendanceOnly: true }));
    assertSubmittedLinks(html, ["18.5204,73.8567", "-33.8688,151.2093"]);
    assert.match(html, /Submission location unavailable/);
    assert.doesNotMatch(html, /q=21[.]125/);
  });
}
