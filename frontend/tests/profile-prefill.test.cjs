const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const ts = require("typescript");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");

function load(file, imports = {}) {
  const module = { exports: {} };
  const script = ts.transpileModule(fs.readFileSync(path.join(__dirname, "..", file), "utf8"), {
    compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
  }).outputText;
  new Function("require", "module", "exports", script)(name => imports[name] ?? require(name), module, module.exports);
  return module.exports;
}
const profileLib = load("lib/student-profile.ts");
const imported = { studentName: "Institutional Student", uid: "23001107", instituteEmail: "student@college.edu", branch: "COMPUTER_ENGINEERING", branchLabel: "Computer Engineering", semester: 7,
  readOnlyFields: ["studentName", "uid", "instituteEmail", "branch", "semester"] };
function nodes(element, type) {
  const found = [];
  function visit(node) {
    if (!node || typeof node !== "object") return;
    if (typeof node.type === "function") return visit(node.type(node.props));
    if (node.type === type) found.push(node);
    React.Children.forEach(node.props?.children, visit);
  }
  visit(element);
  return found;
}
function harness(institutional, existing) {
  const slots = [], requests = [], saved = []; let cursor = 0;
  const { ProfileForm } = load("components/ProfileForm.tsx", {
    react: { useEffect() {}, useState(initial) { const index = cursor++; if (!(index in slots)) slots[index] = typeof initial === "function" ? initial() : initial;
      return [slots[index], update => { slots[index] = typeof update === "function" ? update(slots[index]) : update; }]; } },
    "@/lib/student-profile": profileLib,
    "@/lib/api": { api: Object.fromEntries(["post", "put"].map(method => [method, async (url, payload) => { requests.push({ method, url, payload }); return { data: payload }; }])) }
  });
  function render() { cursor = 0; return ProfileForm({ institutional, existing, onSaved: profile => saved.push(profile) }); }
  return { requests, saved, render, input: name => nodes(render(), "input").find(node => node.props.name === name),
    select: name => nodes(render(), "select").find(node => node.props.name === name),
    submit: () => render().props.onSubmit({ preventDefault() {} }) };
}

test("imported account fields are prefilled, visibly read-only and have no typing handler", () => {
  const h = harness(imported);
  for (const [name, value] of [["studentName", imported.studentName], ["uid", imported.uid], ["instituteEmail", imported.instituteEmail], ["branch", imported.branchLabel], ["semester", "7"]]) {
    const input = h.input(name);
    assert.equal(input.props.value, value);
    assert.equal(input.props.readOnly, true);
    assert.equal(input.props["aria-readonly"], true);
    assert.equal(input.props.onChange, undefined);
    assert.equal(input.props.disabled, undefined);
  }
  assert.match(renderToStaticMarkup(h.render()), /Institution provided · Read-only/);
  assert.equal(h.select("semester"), undefined);
  for (const name of ["personalEmail", "phoneNumber", "rollNumber", "academicYear", "guardianName", "facultyMentorName"]) {
    assert.equal(h.input(name).props.readOnly, false);
    assert.equal(typeof h.input(name).props.onChange, "function");
  }
});

test("editing legitimate contact fields preserves institutional payload values", async () => {
  const h = harness(imported);
  h.input("personalEmail").props.onChange({ target: { value: "updated@example.com" } });
  h.input("phoneNumber").props.onChange({ target: { value: "+91 98765 43219" } });
  await h.submit();
  assert.equal(h.requests.length, 1);
  const { method, url, payload } = h.requests[0];
  assert.equal(method, "post"); assert.equal(url, "/student/profile");
  assert.equal(payload.studentName, imported.studentName); assert.equal(payload.uid, imported.uid);
  assert.equal(payload.instituteEmail, imported.instituteEmail); assert.equal(payload.branch, imported.branch); assert.equal(payload.semester, 7);
  assert.equal(payload.personalEmail, "updated@example.com"); assert.equal(payload.phoneNumber, "+91 98765 43219"); assert.equal(h.saved.length, 1);
});

test("legacy accounts can supply missing UID, name and semester while prefilled email and department stay locked", async () => {
  const h = harness({ ...imported, studentName: null, uid: null, semester: null, readOnlyFields: ["instituteEmail", "branch"] });
  assert.equal(h.input("uid").props.readOnly, false); assert.equal(h.input("studentName").props.readOnly, false);
  assert.equal(h.input("instituteEmail").props.readOnly, true); assert.equal(h.input("branch").props.readOnly, true);
  h.input("uid").props.onChange({ target: { value: "LEGACY-UID" } }); h.input("studentName").props.onChange({ target: { value: "Legacy Student" } });
  h.select("semester").props.onChange({ target: { value: "6" } }); await h.submit();
  assert.equal(h.requests[0].payload.uid, "LEGACY-UID"); assert.equal(h.requests[0].payload.studentName, "Legacy Student"); assert.equal(h.requests[0].payload.semester, 6);
});

test("existing values and identifiers remain intact when account prefill differs", async () => {
  const existing = { ...profileLib.emptyProfile, id: 9, studentName: "Existing Name", uid: "EXISTING-UID", branch: "MECHANICAL", branchLabel: "Mechanical", instituteEmail: "existing@college.edu", semester: 8,
    currentAddress: null, createdAt: "2026-10-13T09:00:00Z", updatedAt: "2026-10-13T09:00:00Z", profileStatus: "COMPLETE", maskedAadhaar: "XXXX XXXX 9012" };
  const h = harness(imported, existing);
  assert.equal(h.input("studentName").props.value, "Existing Name"); assert.equal(h.input("uid").props.value, "EXISTING-UID");
  assert.equal(h.input("branch").props.value, "Mechanical"); assert.equal(h.input("semester").props.value, "8");
  h.input("phoneNumber").props.onChange({ target: { value: "+91 98765 43219" } }); await h.submit();
  assert.equal(h.requests[0].method, "put"); assert.equal(h.requests[0].payload.uid, "EXISTING-UID"); assert.equal(h.requests[0].payload.instituteEmail, "existing@college.edu");
  assert.equal(h.requests[0].payload.studentName, "Existing Name"); assert.equal(h.requests[0].payload.semester, 8); assert.equal(h.requests[0].payload.aadhaarNumber, "");
});
