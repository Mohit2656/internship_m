const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const ts = require("typescript");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");

function load(file, imports, extra = "") {
  const source = fs.readFileSync(path.join(__dirname, "..", file), "utf8") + extra;
  const code = ts.transpileModule(source, { compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText;
  const module = { exports: {} };
  new Function("require", "module", "exports", code)(name => imports[name] ?? require(name), module, module.exports);
  return module.exports;
}

function hooksHarness() {
  const slots = []; let cursor = 0; let effects = [];
  const hooks = {
    useState(initial) { const index = cursor++; if (!(index in slots)) slots[index] = initial; return [slots[index], value => { slots[index] = typeof value === "function" ? value(slots[index]) : value; }]; },
    useRef(initial) { const index = cursor++; if (!(index in slots)) slots[index] = { current: initial }; return slots[index]; },
    useCallback(callback) { cursor++; return callback; },
    useEffect(callback, deps) {
      const index = cursor++; const previous = slots[index];
      if (!previous || deps.some((value, i) => !Object.is(value, previous.deps[i]))) {
        effects.push(() => { previous?.cleanup?.(); slots[index] = { deps, cleanup: callback() }; });
      }
    },
  };
  return { hooks, render(component) { cursor = 0; effects = []; const tree = component(); effects.forEach(effect => effect()); return tree; }, unmount() { slots.forEach(slot => slot?.cleanup?.()); } };
}

function nodes(tree, type) {
  const result = [];
  function walk(node) { if (!node || typeof node !== "object") return; if (node.type === type) result.push(node); React.Children.forEach(node.props?.children, walk); }
  walk(tree); return result;
}
const mentor = { id: 7, userId: 42, name: "Faculty Example", instituteEmail: "faculty@example.edu", employeeId: "EMP-42", department: "Computing", designation: "Professor", phone: "12345", active: true, assignedStudentCount: 2, mustChangePassword: false, createdAt: "2026-10-01T08:00:00Z" };
const flush = () => new Promise(resolve => setImmediate(resolve));

function deletionHarness(remove, changed = () => {}, initialMentor = mentor) {
  const h = hooksHarness(); let current = initialMentor;
  const { DeleteMentorAccountButton } = load("components/DeleteMentorAccountButton.tsx", { react: h.hooks, "@/lib/api": { api: { delete: remove } } });
  const render = () => h.render(() => DeleteMentorAccountButton({ mentor: current, onDeactivated: data => { current = data; changed(data); } }));
  return { render, button: label => nodes(render(), "button").find(node => node.props.children === label), html: () => renderToStaticMarkup(render()), unmount: h.unmount };
}

test("confirmation identifies mentor, describes assignment and history retention, and cancellation sends no request", () => {
  let calls = 0;
  const h = deletionHarness(() => { calls++; });
  h.button("Delete").props.onClick();
  assert.equal(calls, 0);
  const dialog = nodes(h.render(), "dialog")[0];
  assert.equal(dialog.props["aria-labelledby"], "delete-mentor-title-42");
  assert.equal(dialog.props["aria-describedby"], "delete-mentor-description-42");
  for (const expected of [/Faculty Example/, /faculty@example.edu/, /prevent future login/, /2 students are currently assigned/, /assignments will remain in place/, /Student accounts, attendance, reports, approval history, audit records, and historical mentor attribution will be preserved/]) assert.match(h.html(), expected);
  h.button("Cancel").props.onClick();
  assert.equal(calls, 0); assert.equal(nodes(h.render(), "dialog").length, 0);
});

test("delete uses mentor user ID, prevents duplicate requests and escape dismissal while pending, then retains inactive account", async () => {
  let calls = 0, complete, changed;
  const h = deletionHarness(url => { calls++; assert.equal(url, "/admin/mentor-accounts/42"); return new Promise(resolve => { complete = resolve; }); }, data => { changed = data; });
  h.button("Delete").props.onClick();
  const confirm = h.button("Confirm deletion"); confirm.props.onClick(); confirm.props.onClick();
  assert.equal(calls, 1); assert.equal(h.button("Cancel").props.disabled, true); assert.equal(h.button("Deleting...").props.disabled, true);
  let prevented = false;
  nodes(h.render(), "dialog")[0].props.onCancel({ preventDefault() { prevented = true; } });
  assert.equal(prevented, true); assert.equal(nodes(h.render(), "dialog").length, 1);
  const deactivated = { ...mentor, active: false };
  complete({ data: deactivated }); await flush();
  assert.deepEqual(changed, deactivated); assert.equal(changed.assignedStudentCount, 2);
  assert.match(h.html(), /account was deactivated/); assert.match(h.html(), /student assignments and historical records were retained/);
  assert.equal(nodes(h.render(), "dialog").length, 0); assert.equal(h.button("Delete"), undefined);
});

test("authorization errors retain confirmation for retry without exposing backend detail", async () => {
  let changed = 0;
  const h = deletionHarness(async () => { throw { response: { status: 403, data: { message: "sensitive server detail" } } }; }, () => { changed++; });
  h.button("Delete").props.onClick(); h.button("Confirm deletion").props.onClick(); await flush();
  assert.equal(changed, 0); assert.match(h.html(), /do not have permission/); assert.doesNotMatch(h.html(), /sensitive server detail/);
  assert.equal(h.button("Confirm deletion").props.disabled, false); assert.equal(nodes(h.render(), "dialog").length, 1);
});

test("network ambiguity advises refresh and deactivated accounts expose no delete action", async () => {
  const h = deletionHarness(async () => { throw Error("private network data"); });
  h.button("Delete").props.onClick(); h.button("Confirm deletion").props.onClick(); await flush();
  assert.match(h.html(), /Refresh the list before trying again/); assert.doesNotMatch(h.html(), /private network data/);
  assert.equal(deletionHarness(() => assert.fail("No request expected"), () => {}, { ...mentor, active: false }).button("Delete"), undefined);
});

test("late deletion response after navigation does not update an unmounted page", async () => {
  let complete, changed = 0;
  const h = deletionHarness(() => new Promise(resolve => { complete = resolve; }), () => { changed++; });
  h.button("Delete").props.onClick(); h.button("Confirm deletion").props.onClick(); h.unmount();
  complete({ data: { ...mentor, active: false } }); await flush(); assert.equal(changed, 0);
});

test("account page keeps deactivated mentor, assignments, and explicit management while hiding delete/reset controls", async () => {
  const h = hooksHarness(); const DeleteButton = () => null; const AssignmentManager = () => null; const ResetButton = () => null;
  const { MentorAccountsContent } = load("app/admin/mentor-accounts/page.tsx", {
    react: h.hooks, "@/lib/api": { api: { get: async () => ({ data: [mentor] }) } },
    "@/components/DeleteMentorAccountButton": { DeleteMentorAccountButton: DeleteButton },
    "@/components/MentorAssignmentManager": { MentorAssignmentManager: AssignmentManager },
    "@/components/ResetPasswordButton": { ResetPasswordButton: ResetButton },
    "@/components/AppFrame": { AppFrame: () => null }, "@/components/ProtectedRoute": { ProtectedRoute: () => null },
  }, "\nexport { MentorAccountsContent };\n");
  const render = () => h.render(MentorAccountsContent);
  render(); await flush();
  nodes(render(), "button").find(node => node.props.children === "Manage Students").props.onClick();
  assert.equal(nodes(render(), AssignmentManager)[0].props.mentor.active, true);
  nodes(render(), DeleteButton)[0].props.onDeactivated({ ...mentor, active: false });
  const tree = render(); const html = renderToStaticMarkup(tree);
  assert.match(html, /Faculty Example/); assert.match(html, /faculty@example.edu/); assert.match(html, /Deactivated/); assert.match(html, /Login disabled/);
  assert.equal(nodes(tree, ResetButton).length, 0); assert.equal(nodes(tree, DeleteButton)[0].props.mentor.active, false);
  assert.equal(nodes(tree, AssignmentManager)[0].props.mentor.active, false);
  assert.equal(nodes(tree, AssignmentManager)[0].props.mentor.assignedStudentCount, 2);
  assert.ok(nodes(tree, "button").some(node => node.props.children === "Manage Students"));
});

test("inactive mentor assignment workspace preserves removal but offers no new assignment", async () => {
  const h = hooksHarness(); let removals = 0;
  const student = { userId: 5, name: "Assigned Student", uid: "UID-5", profileStatus: "COMPLETE" };
  const workspace = { mentor: { ...mentor, active: false }, totalStudents: 2, assignedStudentsCount: 1, unassignedStudentsCount: 1, assignedStudents: [student], availableStudents: [{ ...student, userId: 6, name: "Available Student" }] };
  const { MentorAssignmentManager } = load("components/MentorAssignmentManager.tsx", { react: h.hooks, "@/lib/api": { api: {
    get: async () => ({ data: workspace }),
    post: () => assert.fail("Inactive mentor must not receive new assignments"),
    delete: async url => { removals++; assert.equal(url, "/admin/mentors/42/students/5"); return { data: { ...workspace, assignedStudents: [], assignedStudentsCount: 0 } }; },
  } } });
  const render = () => h.render(() => MentorAssignmentManager({ mentor: { ...mentor, active: false }, onClose() {}, onCountChanged() {} }));
  render(); await flush();
  const tree = render(); const html = renderToStaticMarkup(tree);
  assert.match(html, /Assigned Student/); assert.match(html, /New assignments are unavailable for a deactivated mentor/);
  assert.equal(nodes(tree, "input").length, 0); assert.ok(!nodes(tree, "button").some(node => String(node.props.children).includes("Assign Selected")));
  nodes(tree, "button").find(node => node.props.children === "Remove").props.onClick(); await flush(); assert.equal(removals, 1);
});
