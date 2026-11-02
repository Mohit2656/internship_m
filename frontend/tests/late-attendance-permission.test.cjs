const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const ts = require("typescript");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");

function harness(component, api, props = {}) {
  const slots = [], effects = []; let cursor = 0;
  const hooks = {
    useState(initial) {
      const index = cursor++;
      if (!(index in slots)) slots[index] = initial;
      return [slots[index], value => { slots[index] = typeof value === "function" ? value(slots[index]) : value; }];
    },
    useRef(initial) { const index = cursor++; return slots[index] ??= { current: initial }; },
    useEffect(effect, deps) {
      const index = cursor++, previous = slots[index];
      if (!previous || deps.some((value, n) => !Object.is(value, previous.deps[n]))) {
        slots[index] = { deps, cleanup: previous?.cleanup };
        effects.push(() => { slots[index].cleanup?.(); slots[index].cleanup = effect(); });
      }
    },
  };
  function compile(file, mocks) {
    const module = { exports: {} };
    new Function("require", "module", "exports", ts.transpileModule(fs.readFileSync(file, "utf8"), {
      compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
    }).outputText)(name => mocks[name] ?? require(name), module, module.exports);
    return module.exports;
  }
  const late = compile(path.join(__dirname, "../lib/late-attendance.ts"), {});
  const module = compile(path.join(__dirname, `../components/${component}.tsx`), {
    react: hooks, "@/lib/api": { api }, "@/lib/late-attendance": late,
    "@/components/DashboardLayout": { DashboardSection: ({ children, title, description, id }) => React.createElement("section", { id }, React.createElement("h2", null, title), React.createElement("p", null, description), children) },
  });
  function render() { cursor = 0; return module[component](props); }
  function nodes(type) {
    const result = [];
    function walk(node) { if (!node || typeof node !== "object") return; if (node.type === type) result.push(node); React.Children.forEach(node.props?.children, walk); }
    walk(render()); return result;
  }
  async function flush() { render(); while (effects.length) effects.shift()(); await new Promise(resolve => setImmediate(resolve)); render(); }
  return {
    html: () => renderToStaticMarkup(render()), nodes, flush,
    async click(label) { const button = nodes("button").find(node => node.props.children === label); assert.ok(button, `Button ${label} exists`); button.props.onClick(); await flush(); },
    change(id, value) { const node = [...nodes("input"), ...nodes("textarea")].find(node => node.props.id === id); assert.ok(node, `Field ${id} exists`); node.props.onChange({ target: { value } }); },
    async submit(index = 0) { const promise = nodes("form")[index].props.onSubmit({ preventDefault() {} }); await promise; await flush(); },
  };
}

const pending = {
  id: 21, studentUserId: 11, internshipId: 6, studentName: "Request Student", uid: "ENR001", attendanceDate: "2026-10-22",
  reason: "Could not submit due to a network outage", supportingDetails: "Supervisor can confirm the task", requestedAt: "2026-10-23T06:00:00Z",
  status: "PENDING", reviewerId: null, reviewedAt: null, rejectionReason: null, resubmissionAllowed: false, attendanceId: null,
  mentorAssigned: true, attendanceZone: "Asia/Kolkata",
};
const reviewed = { ...pending, id: 20, status: "REJECTED", reviewedAt: "2026-10-23T07:00:00Z", rejectionReason: "Include supporting details", resubmissionAllowed: true };

test("mentor queue contains requests only, keeps history separate, and uses independent request view filters", async () => {
  const calls = [];
  const h = harness("LateAttendancePermissions", { async get(url, options) { calls.push({ url, options }); return { data: [pending, reviewed] }; } });
  await h.flush();
  assert.equal(calls[0].url, "/mentor/attendance/late-requests");
  assert.equal(calls[0].options.params.view, "pending");
  assert.match(h.html(), /Request Student/); assert.match(h.html(), /ENR001/); assert.match(h.html(), /network outage/);
  assert.equal(h.nodes("button").filter(node => node.props.children === "Approve").length, 1);
  assert.doesNotMatch(h.html(), /Include supporting details|role="switch"|late-permissions/);
  await h.click("Request history");
  assert.equal(calls[1].options.params.view, "history");
  assert.match(h.html(), /Include supporting details/); assert.match(h.html(), /Resubmission allowed/);
  assert.equal(h.nodes("button").filter(node => node.props.children === "Approve").length, 0);
});

test("approval requires a confirmation and saves one decision even on repeated submit", async () => {
  const calls = []; let resolveDecision; let completed = false;
  const h = harness("LateAttendancePermissions", {
    async get() { return { data: completed ? [] : [pending] }; },
    async post(url, body) { calls.push({ url, body }); await new Promise(resolve => { resolveDecision = resolve; }); completed = true; return { data: { ...pending, status: "APPROVED" } }; }
  });
  await h.flush(); await h.click("Approve");
  assert.equal(calls.length, 0);
  assert.match(h.html(), /GPS and liveness remain unverified/);
  const form = h.nodes("form")[0];
  const first = form.props.onSubmit({ preventDefault() {} });
  await form.props.onSubmit({ preventDefault() {} });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, "/mentor/attendance/late-requests/21/decision");
  assert.deepEqual(calls[0].body, { status: "APPROVED", rejectionReason: null, allowResubmission: false });
  resolveDecision(); await first; await h.flush();
  assert.match(h.html(), /Request for Request Student on 2026-10-22 approved/);
  assert.match(h.html(), /No pending late attendance requests/);
});

test("rejection submits an optional reason and explicit resubmission permission", async () => {
  const calls = [];
  const h = harness("LateAttendancePermissions", {
    async get() { return { data: calls.length ? [] : [pending] }; },
    async post(url, body) { calls.push({ url, body }); return { data: { ...pending, status: "REJECTED" } }; }
  });
  await h.flush(); await h.click("Reject");
  h.change("late-request-rejection", "Please include supervisor details");
  h.nodes("input").find(node => node.props.type === "checkbox").props.onChange({ target: { checked: true } });
  await h.submit();
  assert.deepEqual(calls[0].body, { status: "REJECTED", rejectionReason: "Please include supervisor details", allowResubmission: true });
  assert.match(h.html(), /No pending late attendance requests/);
});

test("IIIC request queue uses the admin scope and an authorization failure preserves the request", async () => {
  const calls = [];
  const h = harness("LateAttendancePermissions", {
    async get(url) { calls.push(url); return { data: [pending] }; },
    async post() { throw { response: { data: { message: "Request is no longer in your authorized scope" } } }; }
  }, { role: "ADMIN" });
  await h.flush(); assert.equal(calls[0], "/admin/attendance/late-requests");
  assert.match(h.html(), /without an active faculty mentor/);
  await h.click("Approve"); await h.submit();
  assert.match(h.html(), /role="alert"/); assert.match(h.html(), /no longer in your authorized scope/);
  assert.match(h.html(), /Request Student/);
});

test("student submits date, reason and details; duplicate dates are blocked and status remains visible", async () => {
  const calls = [];
  const h = harness("StudentLateAttendanceRequests", {
    async get(url, options) { calls.push({ url, options }); return { data: [] }; },
    async post(url, body) { calls.push({ url, body }); return { data: pending }; },
  }, { internship: { id: 6, startDate: "2026-10-01", endDate: "2026-11-30" } });
  await h.flush();
  h.change("late-attendance-date", "2026-10-22"); h.change("late-attendance-reason", pending.reason); h.change("late-attendance-details", pending.supportingDetails);
  await h.submit();
  assert.deepEqual(calls[1], { url: "/student/attendance/late-requests", body: { internshipId: 6, attendanceDate: "2026-10-22", reason: pending.reason, supportingDetails: pending.supportingDetails } });
  assert.match(h.html(), /faculty mentor was notified/); assert.match(h.html(), /Pending/);
  assert.match(h.html(), /already has a pending request/);
  await h.submit(); assert.equal(calls.length, 2);
  assert.equal(h.nodes("button").some(node => node.props.children === "Approve"), false);
});

test("student sees IIIC routing and can resubmit only an explicitly allowed rejected date", async () => {
  let posts = 0;
  const h = harness("StudentLateAttendanceRequests", {
    async get() { return { data: [reviewed] }; },
    async post() { posts++; return { data: { ...pending, mentorAssigned: false } }; },
  }, { internship: { id: 6, startDate: "2026-10-01", endDate: "2026-11-30" } });
  await h.flush(); h.change("late-attendance-date", "2026-10-22"); h.change("late-attendance-reason", "Additional supporting reference");
  assert.doesNotMatch(h.html(), /already has a rejected request/);
  await h.submit(); assert.equal(posts, 1);
  assert.match(h.html(), /submitted to IIIC/); assert.match(h.html(), /Awaiting IIIC review/);
});
