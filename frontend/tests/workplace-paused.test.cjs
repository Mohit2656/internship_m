const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const ts = require("typescript");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");

function load(file, imports = {}) {
  const module = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname, "..", file), "utf8");
  new Function("require", "module", "exports", ts.transpileModule(source, {
    compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
  }).outputText)(name => Object.hasOwn(imports, name) ? imports[name] : require(name), module, module.exports);
  return module.exports;
}

const internship = load("lib/internship-details.ts");
const update = load("components/LastStudentUpdate.tsx");
const view = load("components/InternshipDetailsView.tsx", { "@/components/LastStudentUpdate": update });
const workplace = { placeId: null, name: "Registered branch", address: "Selected branch address", latitude: 18.52, longitude: 73.85 };
const details = { id: 7, companyName: "Existing company", companyType: "IT", companyTypeLabel: "IT", companyAddress: "Existing postal address",
  companyPhone: "9999999999", industrySupervisorName: "Supervisor", industrySupervisorDesignation: "Manager", industrySupervisorContact: "8888888888",
  durationMonths: 3, startDate: "2026-10-01", endDate: "2026-12-31", totalWeeks: 12, internshipMode: "OFFICE_REPORTING", internshipModeLabel: "Office",
  stipendPerMonth: 0, fullTimeEmploymentOffered: false, workplace };

function elements(tree) {
  return !tree || typeof tree !== "object" ? [] : [tree, ...React.Children.toArray(tree.props?.children).flatMap(elements)];
}

function form(initial, saving = false, fieldErrors = {}) {
  const state = [];
  let cursor = 0;
  let submitted;
  const picker = props => React.createElement("div", { "data-workplace-picker": true, "data-disabled": props.disabled }, "OpenStreetMap workplace picker");
  const { InternshipDetailsForm } = load("components/InternshipDetailsForm.tsx", {
    react: { useState(initial) { const index = cursor++; if (!(index in state)) state[index] = initial;
      return [state[index], next => { state[index] = typeof next === "function" ? next(state[index]) : next; }]; } },
    "@/lib/internship-details": internship,
    "@/components/OpenStreetMapWorkplacePicker": { OpenStreetMapWorkplacePicker: picker }
  });
  return {
    render() { cursor = 0; return InternshipDetailsForm({ initial, saving, fieldErrors, onSubmit: data => { submitted = data; } }); },
    select(tree, location) { elements(tree).find(element => element.type === picker).props.onSelect(location); },
    picker(tree) { return elements(tree).find(element => element.type === picker).props; },
    get submitted() { return submitted; }
  };
}

test("company registration preserves saved coordinates and submits a newly confirmed workplace", () => {
  const initial = internship.toFormData(details);
  assert.deepEqual(initial.workplace, workplace);
  const harness = form(initial);
  let tree = harness.render();
  const html = renderToStaticMarkup(tree);
  for (const label of ["Company Name", "Type of Company", "Address of Company", "Company Phone Number", "Industry Supervisor", "Internship Mode"]) assert.ok(html.includes(label));
  assert.match(html, /OpenStreetMap workplace picker|Workplace location/);
  assert.match(html, /assigned mentor and the III Cell/);
  assert.deepEqual(harness.picker(tree).value, workplace);
  assert.equal(harness.picker(tree).address, details.companyAddress);
  const selected = { ...workplace, latitude: 19.076, longitude: 72.8777, address: "Corrected branch address" };
  harness.select(tree, selected);
  tree = harness.render();
  let prevented = false;
  tree.props.onSubmit({ preventDefault() { prevented = true; } });
  assert.equal(prevented, true);
  assert.equal(harness.submitted.companyAddress, details.companyAddress);
  assert.deepEqual(harness.submitted.workplace, selected);
});

test("registration shows missing selection, saving state and backend field errors", () => {
  const initial = internship.toFormData({ ...details, workplace: null });
  assert.equal(initial.workplace, null);
  assert.equal(internship.emptyInternshipDetails.workplace ?? null, null);
  const harness = form(initial, true, { workplace: "Select the company branch before approval" });
  const tree = harness.render();
  assert.equal(harness.picker(tree).value, null);
  assert.equal(harness.picker(tree).disabled, true);
  assert.match(renderToStaticMarkup(tree), /Select the company branch before approval/);
});

test("office registration waits for confirmed selection while online company registration remains usable", () => {
  const office = form(internship.toFormData({ ...details, workplace: null }));
  office.render().props.onSubmit({ preventDefault() {} });
  assert.equal(office.submitted, undefined);
  const online = form(internship.toFormData({ ...details, internshipMode: "ONLINE", workplace: null }));
  online.render().props.onSubmit({ preventDefault() {} });
  assert.equal(online.submitted.companyAddress, details.companyAddress);
  assert.equal(online.submitted.workplace, null);
  const pending = form(internship.toFormData(details));
  pending.picker(pending.render()).onPendingChange(true);
  pending.render().props.onSubmit({ preventDefault() {} });
  assert.equal(pending.submitted, undefined);
  pending.picker(pending.render()).onPendingChange(false);
  pending.render().props.onSubmit({ preventDefault() {} });
  assert.deepEqual(pending.submitted.workplace, workplace);
});

function hooks(values) {
  let index = 0;
  return { useEffect() {}, useState(initial) { const i = index++; return [i in values ? values[i] : initial, () => {}]; } };
}
const children = ({ children }) => children;
const empty = () => null;
const manager = ({ scope = "admin", studentId }) => React.createElement("section", {
  "data-workplace-scope": scope, "data-student-id": studentId
}, `${scope} workplace approval`);
const shared = { "next/link": { default: ({ children, ...props }) => React.createElement("a", props, children) },
  "next/navigation": { useParams: () => ({ id: "7" }) }, "@/lib/api": {},
  "@/components/AppFrame": { AppFrame: children }, "@/components/ProtectedRoute": { ProtectedRoute: children },
  "@/components/InternshipDetailsView": view, "@/lib/internship-details": internship,
  "@/components/LastStudentUpdate": update,
  "@/components/InternshipLocationManager": { InternshipLocationManager: manager } };

test("student internship view preserves company information and shows its own workplace approval panel", () => {
  const { default: Page } = load("app/student/internship/details/page.tsx", { ...shared,
    react: hooks({ 0: { workflowStatus: "INTERNSHIP_DETAILS_COMPLETE" }, 1: details, 2: false }),
    "@/components/InternshipDetailsForm": { InternshipDetailsForm: empty }
  });
  const html = renderToStaticMarkup(React.createElement(Page));
  assert.match(html, /Existing company|Existing postal address/);
  assert.match(html, /data-workplace-scope="student"/);
  assert.doesNotMatch(html, /data-student-id=/);
});

test("admin student view preserves company and hybrid configuration alongside scoped workplace review", () => {
  const { default: Page } = load("app/admin/students/[id]/page.tsx", { ...shared,
    react: hooks({ 0: { id: 7, internshipDetails: { ...details, internshipMode: "HYBRID" } }, 1: false }),
    "@/components/AdminProfessionalProfile": { AdminProfessionalProfile: empty },
    "@/components/HybridScheduleManager": { HybridScheduleManager: () => React.createElement("p", null, "Hybrid configuration") },
    "@/components/InternshipOnboardingDetails": { InternshipOnboardingDetails: empty },
    "@/components/ProfileDetails": { ProfileDetails: empty }, "@/components/StatusBadge": { StatusBadge: empty }
  });
  const html = renderToStaticMarkup(React.createElement(Page));
  assert.match(html, /Existing company|Existing postal address/);
  assert.match(html, /Hybrid configuration/);
  assert.match(html, /data-workplace-scope="admin" data-student-id="7"/);
});

test("mentor student view retains assigned profile and scopes workplace review to that student", () => {
  const { default: Page } = load("app/mentor/students/[id]/page.tsx", { ...shared,
    react: hooks({ 0: { studentName: "Assigned student", confirmedSkills: [] } })
  });
  const html = renderToStaticMarkup(React.createElement(Page));
  assert.match(html, /Assigned student|Professional Profile/);
  assert.match(html, /data-workplace-scope="mentor" data-student-id="7"/);
});
