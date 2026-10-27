const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const ts = require("typescript");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");

function load(file, imports = {}) {
  const m = { exports: {} };
  new Function("require", "module", "exports", ts.transpileModule(fs.readFileSync(path.join(__dirname, "..", file), "utf8"), {
    compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
  }).outputText)(name => imports[name] || require(name), m, m.exports);
  return m.exports;
}

// Run hooks and effect cleanup for each keyed component, including the real nested
// history component. Requests remain under test control, without a browser dependency.
function harness(file, name, props, api) {
  const frames = new Map();
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
    useEffect(effect, deps) {
      const frame = current, index = frame.cursor++, previous = frame.hooks[index];
      if (!previous || !deps || deps.length !== previous.deps?.length || deps.some((value, i) => !Object.is(value, previous.deps[i]))) {
        const state = { deps, cleanup: previous?.cleanup };
        frame.hooks[index] = state;
        pending.push(() => { state.cleanup?.(); state.cleanup = effect(); });
      }
    }
  };
  const component = load(file, {
    react: hooks,
    "@/lib/api": { api },
    "@/components/MentorRecordDelete": { MentorRecordDelete: ({ kind, id, description, onDeleted }) => React.createElement("button", { "data-delete-id": id, "data-delete-kind": kind, title: description, onClick: onDeleted }, "Delete") },
    "@/lib/attendance": load("lib/attendance.ts"),
    "next/link": { default: ({ children, ...rest }) => React.createElement("a", rest, children) }
  })[name];
  function cleanup(frame) { frame.mounted = false; for (const hook of frame.hooks) hook?.cleanup?.(); }
  function resolve(node, location) {
    if (Array.isArray(node)) return node.map((child, index) => {
      const key = child?.key ?? index, resolved = resolve(child, `${location}/${key}`);
      return React.isValidElement(resolved) ? React.cloneElement(resolved, { key }) : resolved;
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
      current = undefined;
      return resolve(output, `${location}/output`);
    }
    return React.cloneElement(node, {}, resolve(node.props.children, `${location}/children`));
  }
  function draw() {
    let passes = 0;
    do {
      assert.ok(++passes < 20, "component hooks should settle");
      dirty = false; pending = []; used = new Set();
      tree = resolve(React.createElement(component, props), "root");
      for (const [location, frame] of frames) if (!used.has(location)) { cleanup(frame); frames.delete(location); }
      for (const effect of pending) effect();
    } while (dirty);
    return tree;
  }
  const h = {
    render() { return renderToStaticMarkup(draw()); },
    tree: draw,
    async flush() { draw(); await new Promise(resolve => setImmediate(resolve)); draw(); },
    async mount() { await h.flush(); },
    unmount() { for (const frame of frames.values()) cleanup(frame); frames.clear(); }
  };
  return h;
}

function findAll(node, predicate) {
  if (Array.isArray(node)) return node.flatMap(child => findAll(child, predicate));
  if (!React.isValidElement(node)) return [];
  return [...(predicate(node) ? [node] : []), ...findAll(node.props.children, predicate)];
}
function studentButtons(h) { return findAll(h.tree(), node => node.type === "button" && "aria-expanded" in node.props); }
function historySections(h) { return findAll(h.tree(), node => node.type === "section" && node.props["aria-label"]?.startsWith("Attendance history for ")); }
function searchInput(h) { return findAll(h.tree(), node => node.type === "input")[0]; }
function deferred() { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; }
const totals = { presentDays: 1, absentDays: 1, totalDays: 2, percentage: 50 };
const summary = (id, studentName = "Assigned student") => ({ studentUserId: id, studentName, uid: `ENR${id}`, branch: "Engineering", facultyMentor: "Faculty", company: "Company", workflowStatus: "INTERNSHIP_DETAILS_COMPLETE", attendance: totals });
const records = [
  { id: 1, attendanceDate: "2026-10-19", attendanceResult: "PRESENT", serverSubmittedAt: "2026-10-19T07:00:00Z", livenessStatus: "VERIFIED", submittedLate: true },
  { id: 2, attendanceDate: "2026-10-20", attendanceResult: "ABSENT", submissionType: "AUTO_ABSENT", livenessStatus: "NOT_APPLICABLE" }
];
const history = (studentUserId, entries = records) => ({ studentUserId, attendanceZone: "Asia/Kolkata", totals, records: entries });

test("student daily history includes automatic absences in totals and preserves submitted verification", async () => {
  const dailyRecords = [
    { id: 1, attendanceDate: "2026-10-19", attendanceResult: "ABSENT", automaticAbsent: true, submissionType: "AUTO_ABSENT", livenessStatus: "NOT_APPLICABLE" },
    { id: 2, attendanceDate: "2026-10-16", attendanceResult: "PRESENT", submissionType: "REMOTE_CAPTURE", livenessStatus: "VERIFIED" }
  ];
  const calls = [];
  const h = harness("components/DailyAttendanceHistory.tsx", "DailyAttendanceHistory", {}, { async get(url) { calls.push(url); return { data: dailyRecords }; } });
  await h.mount(); const html = h.render();
  assert.deepEqual(calls, ["/student/attendance"]); assert.match(html, /2 daily records · 1 present · 1 absent/);
  assert.match(html, /2026-10-19/); assert.match(html, /Absent/); assert.match(html, /Automatic — no submission before deadline/);
  assert.match(html, /Liveness verified/); assert.match(html, /Student submission/);
  h.unmount();
});

for (const role of ["ADMIN", "MENTOR"]) test(`${role} dashboard lazily expands and collapses individual students while preserving search`, async () => {
  const calls = [], prefix = `/${role.toLowerCase()}/attendance/students`;
  const students = [summary(42, "Alex Student"), summary(43, "Alex Student"), summary(44, "Other Student")];
  const h = harness("components/AttendanceAbsenceOverview.tsx", "AttendanceAbsenceOverview", { role }, { async get(url) { calls.push(url); return { data: url === prefix ? students : history(Number(url.split("/").at(-1))) }; } });
  await h.mount();
  assert.deepEqual(calls, [prefix], "collapsed students must not prefetch attendance");
  assert.equal(studentButtons(h).length, 3);
  assert.ok(studentButtons(h).every(button => button.props["aria-expanded"] === false));
  assert.equal(historySections(h).length, 0);
  assert.doesNotMatch(h.render(), /2026-10-19/);
  assert.match(h.render(), /50.0%/);

  searchInput(h).props.onChange({ target: { value: "Alex" } });
  assert.equal(studentButtons(h).length, 2);
  studentButtons(h)[0].props.onClick();
  await h.flush();
  assert.deepEqual(calls, [prefix, `${prefix}/42`]);
  assert.equal(searchInput(h).props.value, "Alex");
  assert.equal(studentButtons(h).length, 2, "the student list stays visible with history open");
  assert.deepEqual(studentButtons(h).map(button => button.props["aria-expanded"]), [true, false]);
  assert.equal(historySections(h).length, 1);
  assert.equal(historySections(h)[0].props.id, studentButtons(h)[0].props["aria-controls"]);
  assert.match(h.render(), /2026-10-19/);
  assert.match(h.render(), /2026-10-20/);

  studentButtons(h)[1].props.onClick();
  await h.flush();
  assert.deepEqual(calls, [prefix, `${prefix}/42`, `${prefix}/43`], "same-name accounts use their distinct IDs");
  assert.deepEqual(studentButtons(h).map(button => button.props["aria-expanded"]), [false, true]);
  assert.equal(historySections(h).length, 1);
  assert.equal(historySections(h)[0].props.id, studentButtons(h)[1].props["aria-controls"]);

  studentButtons(h)[1].props.onClick();
  assert.equal(historySections(h).length, 0);
  assert.ok(studentButtons(h).every(button => button.props["aria-expanded"] === false));
  assert.equal(searchInput(h).props.value, "Alex");
  assert.doesNotMatch(h.render(), /2026-10-19/);
  assert.equal(calls.length, 3, "collapsing does not issue a request");
  studentButtons(h)[0].props.onClick();
  await h.flush();
  const back = findAll(h.tree(), node => node.type === "button" && node.props.children === "Back to student list")[0];
  back.props.onClick();
  assert.equal(historySections(h).length, 0);
  assert.equal(searchInput(h).props.value, "Alex");
  h.unmount();
});

test("switching and collapsing students aborts pending history and excludes stale records", async () => {
  const requests = [];
  const h = harness("components/AttendanceAbsenceOverview.tsx", "AttendanceAbsenceOverview", { role: "MENTOR" }, { get(url, { signal }) {
    if (url === "/mentor/attendance/students") return Promise.resolve({ data: [summary(42, "Alex"), summary(43, "Alex")] });
    const request = { url, signal, ...deferred() }; requests.push(request); return request.promise;
  } });
  await h.mount();
  studentButtons(h)[0].props.onClick();
  assert.match(h.render(), /Loading attendance history/);
  assert.equal(requests.length, 1);
  studentButtons(h)[1].props.onClick();
  assert.match(h.render(), /Loading attendance history/);
  assert.equal(requests[0].signal.aborted, true);
  assert.equal(requests[1].signal.aborted, false);
  requests[0].resolve({ data: history(42, [{ ...records[0], attendanceDate: "2026-09-01" }]) });
  requests[1].resolve({ data: history(43, [{ ...records[0], attendanceDate: "2026-10-21" }]) });
  await h.flush();
  assert.match(h.render(), /2026-10-21/);
  assert.doesNotMatch(h.render(), /2026-09-01/);
  studentButtons(h)[0].props.onClick();
  h.tree();
  assert.equal(requests.length, 3);
  studentButtons(h)[0].props.onClick();
  assert.equal(historySections(h).length, 0);
  assert.equal(requests[2].signal.aborted, true);
  requests[2].reject(new Error("aborted request completed late"));
  await h.flush();
  assert.doesNotMatch(h.render(), /Access may have changed|2026-10-21/);
  assert.equal(studentButtons(h).length, 2);
  h.unmount();
});

for (const role of ["ADMIN", "MENTOR"]) test(`${role} history preserves chronological records, verification fields, totals and role actions`, async () => {
  const calls = [];
  const h = harness("components/AttendanceAbsenceOverview.tsx", "StudentAttendanceHistory", { role, studentId: 42, name: "Assigned student", onBack() {} }, { async get(url) { calls.push(url); return { data: history(42) }; } });
  await h.mount(); const html = h.render();
  assert.deepEqual(calls, [`/${role.toLowerCase()}/attendance/students/42`]);
  assert.match(html, /1 present/); assert.match(html, /1 absent/); assert.match(html, /50.0%/);
  assert.ok(html.indexOf("2026-10-19") < html.indexOf("2026-10-20"));
  assert.match(html, /PRESENT/); assert.match(html, /ABSENT/);
  assert.match(html, /Back to student list/); assert.match(html, /mentor permission/); assert.match(html, /Liveness verified/);
  assert.match(html, /automatic absence/); assert.match(html, /Asia\/Kolkata/);
  const deletionButtons = findAll(h.tree(), node => node.type === "button" && "data-delete-id" in node.props);
  assert.equal(deletionButtons.length, role === "MENTOR" ? 2 : 0);
  if (role === "MENTOR") {
    assert.deepEqual(deletionButtons.map(button => button.props["data-delete-id"]), [1, 2]);
    assert.ok(deletionButtons.every(button => button.props["data-delete-kind"] === "attendance"));
  }
  h.unmount();
});

test("history loading, empty and API error states leave student selection usable", async () => {
  const pending = deferred();
  const h = harness("components/AttendanceAbsenceOverview.tsx", "AttendanceAbsenceOverview", { role: "MENTOR" }, { get(url) {
    if (url === "/mentor/attendance/students") return Promise.resolve({ data: [summary(42), summary(43, "No attendance student")] });
    if (url.endsWith("/43")) return Promise.resolve({ data: history(43, []) });
    return pending.promise;
  } });
  await h.mount();
  studentButtons(h)[0].props.onClick();
  assert.match(h.render(), /Loading attendance history/);
  pending.reject(new Error("not authorized"));
  await h.flush();
  assert.match(h.render(), /role="alert"/);
  assert.match(h.render(), /Attendance history could not be loaded/);
  assert.equal(studentButtons(h).length, 2);
  studentButtons(h)[1].props.onClick();
  await h.flush();
  assert.match(h.render(), /No attendance records yet/);
  assert.doesNotMatch(h.render(), /Attendance history could not be loaded/);
  assert.equal(findAll(h.tree(), node => node.type === "button" && "data-delete-id" in node.props).length, 0);
  studentButtons(h)[1].props.onClick();
  assert.equal(historySections(h).length, 0);
  h.unmount();
});

test("mentor deletion refreshes both history and summaries once without collapsing or resetting search", async () => {
  const calls = [];
  let historyRequests = 0;
  const h = harness("components/AttendanceAbsenceOverview.tsx", "AttendanceAbsenceOverview", { role: "MENTOR" }, { async get(url) {
    calls.push(url);
    if (url === "/mentor/attendance/students") return { data: [summary(42)] };
    return { data: history(42, ++historyRequests === 1 ? records : [records[1]]) };
  } });
  await h.mount();
  searchInput(h).props.onChange({ target: { value: "ENR42" } });
  studentButtons(h)[0].props.onClick();
  await h.flush();
  findAll(h.tree(), node => node.type === "button" && node.props["data-delete-id"] === 1)[0].props.onClick();
  await h.flush();
  assert.equal(calls.filter(url => url === "/mentor/attendance/students").length, 2);
  assert.equal(calls.filter(url => url === "/mentor/attendance/students/42").length, 2);
  assert.equal(studentButtons(h)[0].props["aria-expanded"], true);
  assert.equal(historySections(h).length, 1);
  assert.equal(searchInput(h).props.value, "ENR42");
  assert.doesNotMatch(h.render(), /2026-10-19/);
  assert.match(h.render(), /2026-10-20/);
  h.unmount();
});

for (const failure of [false, true]) test(`student summary ${failure ? "failure" : "empty state"} is displayed without requesting histories`, async () => {
  const calls = [];
  const h = harness("components/AttendanceAbsenceOverview.tsx", "AttendanceAbsenceOverview", { role: "MENTOR" }, { async get(url) { calls.push(url); if (failure) throw new Error("unavailable"); return { data: [] }; } });
  await h.mount();
  assert.match(h.render(), failure ? /Student attendance summaries could not be loaded/ : /No matching students/);
  assert.deepEqual(calls, ["/mentor/attendance/students"]);
  assert.equal(historySections(h).length, 0);
  h.unmount();
});
