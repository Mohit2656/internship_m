const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const ts = require("typescript");
const React = require("react");

function load(file, imports = {}) {
  const module = { exports: {} };
  const script = ts.transpileModule(fs.readFileSync(path.join(__dirname, "..", file), "utf8"), {
    compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
  }).outputText;
  new Function("require", "module", "exports", script)(name => imports[name] ?? require(name), module, module.exports);
  return module.exports;
}
const profileLib = load("lib/student-profile.ts");
const imported = {
  studentName: "Institutional Student",
  uid: "23001107",
  instituteEmail: "student@college.edu",
  branch: "COMPUTER_ENGINEERING",
  branchLabel: "Computer Engineering",
  semester: 7,
  readOnlyFields: ["studentName", "uid", "instituteEmail", "branch", "semester"]
};

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
    react: {
      useEffect() {},
      useState(initial) {
        const index = cursor++;
        if (!(index in slots)) slots[index] = typeof initial === "function" ? initial() : initial;
        return [slots[index], update => { slots[index] = typeof update === "function" ? update(slots[index]) : update; }];
      }
    },
    "@/lib/student-profile": profileLib,
    "@/lib/api": {
      api: Object.fromEntries(["post", "put"].map(method => [method, async (url, payload) => {
        requests.push({ method, url, payload });
        return { data: payload };
      }]))
    }
  });
  function render() { cursor = 0; return ProfileForm({ institutional, existing, onSaved: profile => saved.push(profile) }); }
  return {
    requests,
    saved,
    render,
    input: name => nodes(render(), "input").find(node => node.props.name === name),
    select: name => nodes(render(), "select").find(node => node.props.name === name),
    textarea: name => nodes(render(), "textarea").find(node => node.props.name === name),
    submit: () => render().props.onSubmit({ preventDefault() {} })
  };
}

test("all student profile fields including prefilled fields are editable", () => {
  const h = harness(imported);
  assert.equal(h.select("branch").props.value, imported.branch);
  assert.equal(typeof h.select("branch").props.onChange, "function");
  assert.equal(h.select("semester").props.value, "7");
  assert.equal(typeof h.select("semester").props.onChange, "function");

  for (const [name, value] of [["studentName", imported.studentName], ["instituteEmail", imported.instituteEmail], ["uid", imported.uid]]) {
    const input = h.input(name);
    assert.equal(input.props.value, value);
    assert.equal(input.props.readOnly, false);
    assert.equal(typeof input.props.onChange, "function");
  }

  for (const name of ["personalEmail", "phoneNumber", "academicYear", "guardianName", "guardianContact", "emergencyContact", "internshipCoordinatorName", "internshipCoordinatorContact", "cdcCoordinatorName", "cdcCoordinatorContact", "aadhaarNumber"]) {
    const input = h.input(name);
    assert.ok(input, `Expected input for ${name}`);
    assert.equal(input.props.readOnly, false);
    assert.equal(typeof input.props.onChange, "function");
  }

  // Faculty mentor assignment is managed by III Cell, not entered manually by students
  assert.equal(h.input("facultyMentorName"), undefined);
  assert.equal(h.input("facultyMentorContact"), undefined);

  // Ensure fields are not forced required on HTML form level
  assert.equal(h.input("studentName").props.required, undefined);
  assert.equal(h.select("bloodGroup").props.required, undefined);
  assert.equal(h.textarea("permanentAddress").props.required, undefined);
});

test("editing name, institutional email, branch, and semester sends updated profile values", async () => {
  const h = harness(imported);
  h.input("personalEmail").props.onChange({ target: { value: "updated@example.com" } });
  h.input("phoneNumber").props.onChange({ target: { value: "+91 98765 43219" } });
  h.input("uid").props.onChange({ target: { value: "UPDATED-UID" } });
  h.input("studentName").props.onChange({ target: { value: "Updated Student" } });
  h.input("instituteEmail").props.onChange({ target: { value: "updated.student@college.edu" } });
  h.select("branch").props.onChange({ target: { value: "INFORMATION_TECHNOLOGY" } });
  h.select("semester").props.onChange({ target: { value: "8" } });
  await h.submit();

  assert.equal(h.requests.length, 1);
  const { method, url, payload } = h.requests[0];
  assert.equal(method, "post");
  assert.equal(url, "/student/profile");
  assert.equal(payload.studentName, "Updated Student");
  assert.equal(payload.uid, "UPDATED-UID");
  assert.equal(payload.rollNumber, "UPDATED-UID");
  assert.equal(payload.instituteEmail, "updated.student@college.edu");
  assert.equal(payload.branch, "INFORMATION_TECHNOLOGY");
  assert.equal(payload.semester, 8);
  assert.equal(payload.personalEmail, "updated@example.com");
  assert.equal(payload.phoneNumber, "+91 98765 43219");
  assert.equal(h.saved.length, 1);
});

test("saving partially completed profile information without requiring every field", async () => {
  const h = harness({ ...imported, studentName: null, uid: null, semester: null });
  h.input("personalEmail").props.onChange({ target: { value: "partial@college.edu" } });
  h.input("phoneNumber").props.onChange({ target: { value: "+91 98765 43210" } });
  await h.submit();

  assert.equal(h.requests.length, 1);
  const { payload } = h.requests[0];
  assert.equal(payload.personalEmail, "partial@college.edu");
  assert.equal(payload.phoneNumber, "+91 98765 43210");
  assert.equal(payload.permanentAddress, "");
  assert.equal(payload.bloodGroup, "");
  assert.equal(payload.semester, null);
});

test("existing values and identifiers remain intact when account prefill differs", async () => {
  const existing = {
    ...profileLib.emptyProfile,
    id: 9,
    studentName: "Existing Name",
    uid: "EXISTING-UID",
    branch: "MECHANICAL",
    branchLabel: "Mechanical",
    instituteEmail: "existing@college.edu",
    semester: 8,
    currentAddress: null,
    createdAt: "2026-10-13T09:00:00Z",
    updatedAt: "2026-10-13T09:00:00Z",
    profileStatus: "COMPLETE",
    maskedAadhaar: "XXXX XXXX 9012"
  };
  const h = harness(imported, existing);
  assert.equal(h.input("studentName").props.value, "Existing Name");
  assert.equal(h.input("uid").props.value, "EXISTING-UID");
  assert.equal(h.input("studentName").props.readOnly, false);
  assert.equal(h.input("instituteEmail").props.readOnly, false);
  assert.equal(h.select("branch").props.value, "MECHANICAL");
  assert.equal(h.select("semester").props.value, "8");

  h.input("phoneNumber").props.onChange({ target: { value: "+91 98765 43219" } });
  h.input("studentName").props.onChange({ target: { value: "Updated Existing Name" } });
  h.input("instituteEmail").props.onChange({ target: { value: "updated.existing@college.edu" } });
  await h.submit();

  assert.equal(h.requests[0].method, "put");
  assert.equal(h.requests[0].payload.uid, "EXISTING-UID");
  assert.equal(h.requests[0].payload.instituteEmail, "updated.existing@college.edu");
  assert.equal(h.requests[0].payload.studentName, "Updated Existing Name");
  assert.equal(h.requests[0].payload.semester, 8);
  assert.equal(h.requests[0].payload.aadhaarNumber, "");
});
