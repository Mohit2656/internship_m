const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs"), path = require("node:path"), ts = require("typescript");
const React = require("react"), { renderToStaticMarkup } = require("react-dom/server");

function harness(name, props, api) {
  const frames = new Map(), modules = new Map();
  let current, pending, used, dirty, tree;
  const hooks = {
    ...React,
    useState(initial) {
      const frame = current, index = frame.cursor++;
      if (!(index in frame.hooks)) frame.hooks[index] = { value: typeof initial === "function" ? initial() : initial };
      const state = frame.hooks[index];
      return [state.value, value => {
        if (!frame.mounted) return;
        const next = typeof value === "function" ? value(state.value) : value;
        if (!Object.is(next, state.value)) { state.value = next; dirty = true; }
      }];
    },
    useRef(initial) {
      const frame = current, index = frame.cursor++;
      if (!(index in frame.hooks)) frame.hooks[index] = { current: initial };
      return frame.hooks[index];
    },
    useEffect(effect, deps) {
      const frame = current, index = frame.cursor++, previous = frame.hooks[index];
      if (!previous || deps.some((value, index) => !Object.is(value, previous.deps[index]))) {
        const state = { deps, cleanup: previous?.cleanup };
        frame.hooks[index] = state;
        pending.push(() => { state.cleanup?.(); state.cleanup = effect(); });
      }
    },
  };
  const imports = {
    react: hooks,
    "@/lib/api": { api },
    "@/lib/monthly-progress": { formatDateTime: value => value },
    "@/lib/professional-profile": { resumeError: (cause, fallback) => cause?.message || fallback },
    "next/link": { default: ({ children, ...rest }) => React.createElement("a", rest, children) },
    "@/components/MentorRecordDelete": { MentorRecordDelete: ({ id, kind, onDeleted }) => React.createElement("button", { "data-delete-id": id, "data-delete-kind": kind, onClick: onDeleted }, "Delete") },
  };
  function load(file) {
    if (modules.has(file)) return modules.get(file);
    const module = { exports: {} };
    modules.set(file, module.exports);
    const output = ts.transpileModule(fs.readFileSync(path.join(__dirname, "..", file), "utf8"), { compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText;
    new Function("require", "module", "exports", output)(request => {
      if (imports[request]) return imports[request];
      if (request.startsWith("@/")) return load(`${request.slice(2)}.${request.startsWith("@/lib/") ? "ts" : "tsx"}`);
      return require(request);
    }, module, module.exports);
    return module.exports;
  }
  const component = load(name === "AttendanceRecords" ? "components/AttendanceRecords.tsx" : "components/AttendanceAbsenceOverview.tsx")[name];
  function cleanup(frame) { frame.mounted = false; for (const hook of frame.hooks) hook?.cleanup?.(); }
  function resolve(node, location) {
    if (Array.isArray(node)) return node.map((child, index) => {
      const key = child?.key ?? index, output = resolve(child, `${location}/${key}`);
      return React.isValidElement(output) ? React.cloneElement(output, { key }) : output;
    });
    if (!React.isValidElement(node)) return node;
    if (typeof node.type === "function") {
      let frame = frames.get(location);
      if (frame?.type !== node.type) {
        if (frame) cleanup(frame);
        frame = { type: node.type, hooks: [], mounted: true };
        frames.set(location, frame);
      }
      used.add(location); frame.cursor = 0; current = frame;
      const output = node.type(node.props);
      return resolve(output, `${location}/output`);
    }
    return React.cloneElement(node, {}, resolve(node.props.children, `${location}/children`));
  }
  function draw() {
    let passes = 0;
    do {
      assert.ok(++passes < 20, "component effects must settle");
      pending = []; used = new Set(); dirty = false;
      tree = resolve(React.createElement(component, props), "root");
      for (const [location, frame] of frames) if (!used.has(location)) { cleanup(frame); frames.delete(location); }
      for (const effect of pending) effect();
    } while (dirty);
    return tree;
  }
  return {
    tree: draw,
    html: () => renderToStaticMarkup(draw()),
    async flush() { draw(); await new Promise(resolve => setImmediate(resolve)); draw(); },
    close() { for (const frame of frames.values()) cleanup(frame); },
  };
}
function all(node, predicate) {
  if (Array.isArray(node)) return node.flatMap(child => all(child, predicate));
  if (!React.isValidElement(node)) return [];
  return [...(predicate(node) ? [node] : []), ...all(node.props.children, predicate)];
}
function find(h, predicate) { return all(h.tree(), predicate)[0]; }
function tableRows(h) { return all(h.tree(), node => node.type === "tbody").flatMap(body => all(body, node => node.type === "tr")); }
function dateCells(h) { return tableRows(h).map(row => all(row, node => node.type === "td")[0].props.children); }
function chooseStudent(h, id) { find(h, node => node.type === "select").props.onChange({ target: { value: String(id) } }); }
function filter(h, label, value) {
  const field = find(h, node => node.type === "label" && React.Children.toArray(node.props.children)[0] === label);
  find({ tree: () => field }, node => node.type === "input" || node.type === "select").props.onChange({ target: { value } });
}
const totals = { presentDays: 88, absentDays: 12, totalDays: 100, percentage: 88 };
const student = (id, studentName = "Alex Student") => ({ studentUserId: id, studentName, uid: `ENR${id}`, branch: "Engineering", company: "Company", facultyMentor: "Faculty A", attendance: totals, workflowStatus: "INTERNSHIP_DETAILS_COMPLETE" });
const record = (id, evidence, overrides = {}) => ({ id, attendanceDate: `2026-10-${19 + id}`, attendanceResult: "PENDING_MENTOR_REVIEW", livenessStatus: "VERIFIED", serverSubmittedAt: "2026-10-20T05:00:00Z", submissionType: "PHYSICAL_CAPTURE", review: { status: "PENDING", revision: 2, approvalAllowed: true }, evidence, ...overrides });
const records = [
  record(1, { latitude: 18.5204, longitude: 73.8567, gpsAccuracyMeters: 12, locationVerificationStatus: "OUTSIDE_RADIUS", dailyWorkSummary: "Worked on a project" }, { review: { status: "PENDING", approvalAllowed: false } }),
  record(2, { latitude: -33.8688, longitude: 151.2093, locationVerificationStatus: "VERIFIED" }, { attendanceResult: "PRESENT", livenessStatus: "FAILED", review: { status: "APPROVED" } }),
  record(3, null, { attendanceResult: "ABSENT", automaticAbsent: true, submissionType: "AUTO_ABSENT", livenessStatus: "NOT_APPLICABLE", review: { status: "NOT_APPLICABLE" } }),
  record(4, null, { attendanceResult: "PRESENT", livenessStatus: "HISTORICAL", review: null }),
  record(5, { latitude: 0, longitude: 0, locationVerificationStatus: "VERIFIED" }),
  record(6, { latitude: 91, longitude: 73, expectedLatitude: 21.125, expectedLongitude: 79.0875, locationVerificationStatus: "VERIFIED" }, { review: { status: "MANUAL_REVIEW", approvalAllowed: false } }),
];
const history = (id, entries = records) => ({ studentUserId: id, attendanceZone: "Asia/Kolkata", totals, records: entries });

for (const role of ["MENTOR", "ADMIN"]) test(`${role} records are separate from summaries and preserve raw GPS, liveness and role actions`, async t => {
  const calls = [], prefix = `/${role.toLowerCase()}/attendance/students`;
  const h = harness("AttendanceRecords", { role }, { async get(url) { calls.push(url); return { data: url === prefix ? [student(42), student(43, "Second Student")] : history(Number(url.split("/").at(-1))) }; } });
  t.after(() => h.close());
  await h.flush();
  assert.deepEqual(calls, [prefix]);
  assert.match(h.html(), /Choose a student/); assert.doesNotMatch(h.html(), /88[.]0%|Attendance Percentage|Report filters|PDF|Excel/);
  chooseStudent(h, 42); await h.flush();
  const html = h.html();
  assert.deepEqual(calls, [prefix, `${prefix}/42`]);
  assert.match(html, /ENR42/); assert.match(html, /Engineering/); assert.match(html, /Company/); assert.match(html, new RegExp(`href="/${role.toLowerCase()}/students/42"`));
  assert.match(html, /GPS verification/); assert.match(html, /Submitted location/); assert.match(html, /Mentor approval/);
  assert.match(html, /Liveness verified/); assert.match(html, /Liveness not confirmed/); assert.match(html, /Location outside the permitted workplace radius/); assert.match(html, /Workplace location verified/);
  assert.match(html, /Worked on a project/); assert.match(html, /Asia\/Kolkata/); assert.match(html, /Not applicable — automatic absence/);
  const maps = all(h.tree(), node => node.type === "a" && node.props.href.startsWith("https://www.google.com/maps"));
  assert.deepEqual(maps.map(link => new URL(link.props.href).searchParams.get("q")), ["18.5204,73.8567", "-33.8688,151.2093", "0,0"]);
  assert.ok(maps.every(link => link.props.target === "_blank" && link.props.rel === "noopener noreferrer"));
  assert.equal((html.match(/Submission location unavailable for this attendance record/g) || []).length, 2);
  assert.doesNotMatch(html, /q=21[.]125|88 present|100 applicable|88[.]0%|Export|Download PDF|Download Excel/);
  const deletes = all(h.tree(), node => node.type === "button" && "data-delete-id" in node.props);
  const approvalButtons = all(h.tree(), node => node.type === "button" && node.props.children === "Mark present");
  assert.equal(deletes.length, role === "MENTOR" ? records.length : 0);
  assert.equal(approvalButtons.length, role === "MENTOR" ? 3 : 0);
  if (role === "MENTOR") assert.deepEqual(approvalButtons.map(button => button.props.disabled), [true, false, true]);
  else { assert.match(html, /Faculty A/); assert.doesNotMatch(html, /Mark absent|Keep pending|Mark as Seen/); }
});

for (const role of ["MENTOR", "ADMIN"]) test(`${role} individual record filters are local and independent and preserve mutation refresh`, async t => {
  const calls = [], prefix = `/${role.toLowerCase()}/attendance/students`;
  const h = harness("StudentAttendanceHistory", { role, studentId: 42, name: "Alex Student", onBack() {}, showSummary: false, recordFilters: true }, { async get(url) { calls.push(url); return { data: history(42) }; } });
  t.after(() => h.close()); await h.flush();
  assert.equal(tableRows(h).length, 6);
  filter(h, "Attendance status", "PRESENT"); assert.deepEqual(dateCells(h), ["2026-10-21", "2026-10-23"]);
  filter(h, "GPS verification", "VERIFIED"); assert.deepEqual(dateCells(h), ["2026-10-21"]);
  filter(h, "Approval status", "APPROVED"); assert.deepEqual(dateCells(h), ["2026-10-21"]);
  filter(h, "From date", "2026-10-22"); assert.match(h.html(), /No attendance records match these filters/);
  find(h, node => node.type === "button" && node.props.children === "Clear record filters").props.onClick();
  assert.equal(tableRows(h).length, 6);
  filter(h, "GPS verification", "NOT_APPLICABLE"); assert.deepEqual(dateCells(h), ["2026-10-22"]);
  filter(h, "GPS verification", "NOT_ASSESSED"); assert.deepEqual(dateCells(h), ["2026-10-23"]);
  filter(h, "GPS verification", ""); filter(h, "To date", "2026-10-20"); filter(h, "From date", "2026-10-25");
  assert.match(h.html(), /From date must be on or before To date/); assert.equal(tableRows(h).length, 0);
  find(h, node => node.type === "button" && node.props.children === "Clear record filters").props.onClick();
  assert.deepEqual(calls, [`${prefix}/42`], "record filters never query report APIs or refetch history");
  if (role === "MENTOR") {
    filter(h, "GPS verification", "OUTSIDE_RADIUS");
    find(h, node => node.type === "button" && node.props["data-delete-id"] === 1).props.onClick(); await h.flush();
    assert.deepEqual(calls, [`${prefix}/42`, `${prefix}/42`]); assert.deepEqual(dateCells(h), ["2026-10-20"], "refresh retains record filters");
  }
});

test("student selection cancels stale histories and keeps search and selected identity scoped", async t => {
  const requests = [];
  const h = harness("AttendanceRecords", { role: "MENTOR" }, { get(url, { signal }) {
    if (url === "/mentor/attendance/students") return Promise.resolve({ data: [student(42), student(43)] });
    let resolve; const promise = new Promise(yes => { resolve = yes; }); requests.push({ url, signal, resolve }); return promise;
  } });
  t.after(() => h.close()); await h.flush();
  chooseStudent(h, 42); h.tree(); assert.match(h.html(), /Loading attendance history/);
  chooseStudent(h, 43); h.tree(); assert.equal(requests[0].signal.aborted, true);
  requests[0].resolve({ data: history(42, [record(1, null)]) }); requests[1].resolve({ data: history(43, [record(2, null)]) });
  await h.flush(); assert.deepEqual(dateCells(h), ["2026-10-21"]); assert.match(h.html(), /ENR43/);
  find(h, node => node.type === "input" && node.props.type === "search").props.onChange({ target: { value: "ENR42" } });
  assert.equal(tableRows(h).length, 0); assert.equal(find(h, node => node.type === "select").props.value, "");
  assert.equal(all(h.tree(), node => node.type === "option").length, 2);
  assert.equal(requests.length, 2);
});

test("missing and invalid evidence never fabricates saved coordinates in records mode", async t => {
  const evidence = [undefined, null, {}, { latitude: null, longitude: null }, { latitude: 18.52 }, { latitude: NaN, longitude: 73 }, { latitude: 18, longitude: Infinity }, { latitude: "18.52", longitude: 73 }, { latitude: 18, longitude: -181 }, { expectedLatitude: 21.125, expectedLongitude: 79.0875, workplaceAddress: "Registered workplace" }];
  const h = harness("StudentAttendanceHistory", { role: "ADMIN", studentId: 42, name: "Alex", onBack() {}, showSummary: false, recordFilters: true }, { async get() { return { data: history(42, evidence.map((value, index) => record(index + 1, value))) }; } });
  t.after(() => h.close()); await h.flush();
  assert.equal((h.html().match(/Submission location unavailable for this attendance record/g) || []).length, evidence.length);
  assert.doesNotMatch(h.html(), /google[.]com\/maps|Registered workplace|88[.]0%/);
});

for (const failure of [false, true]) test(`records student list ${failure ? "authorization error" : "empty state"} does not request histories`, async t => {
  const calls = [];
  const h = harness("AttendanceRecords", { role: "MENTOR" }, { async get(url) { calls.push(url); if (failure) throw new Error("Forbidden"); return { data: [] }; } });
  t.after(() => h.close()); await h.flush();
  assert.deepEqual(calls, ["/mentor/attendance/students"]);
  assert.match(h.html(), failure ? /Students could not be loaded. Access may have changed/ : /No students are currently assigned/);
  assert.equal(tableRows(h).length, 0);
});
