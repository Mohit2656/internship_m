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
  }).outputText)(name => {
    if (/InternshipLocationManager|WorkplacePlaceSelector/.test(name)) throw new Error("Paused workplace integration must not load in active pages");
    return imports[name] || require(name);
  }, module, module.exports);
  return module.exports;
}
const internship = load("lib/internship-details.ts");
const view = load("components/InternshipDetailsView.tsx");
const details = { id: 7, companyName: "Existing company", companyType: "IT", companyTypeLabel: "IT", companyAddress: "Existing postal address",
  companyPhone: "9999999999", industrySupervisorName: "Supervisor", industrySupervisorDesignation: "Manager", industrySupervisorContact: "8888888888",
  durationMonths: 3, startDate: "2026-10-01", endDate: "2026-12-31", totalWeeks: 12, internshipMode: "OFFICE_REPORTING", internshipModeLabel: "Office",
  stipendPerMonth: 0, fullTimeEmploymentOffered: false,
  workplace: { name: "Historical branch", address: "Private historical address", latitude: 18, longitude: 73 } };
const forbidden = /Registered workplace|Google Places|Latitude|Longitude|radius|Approve workplace|Correct registered workplace|Historical branch|Private historical address/;

test("company registration remains usable without workplace selection and excludes legacy workplace payload", () => {
  let submitted;
  const formData = internship.toFormData(details);
  assert.equal(Object.hasOwn(formData, "workplace"), false);
  assert.equal(Object.hasOwn(internship.emptyInternshipDetails, "workplace"), false);
  const { InternshipDetailsForm } = load("components/InternshipDetailsForm.tsx", {
    react: { useState: initial => [initial, () => {}] }, "@/lib/internship-details": internship
  });
  const tree = InternshipDetailsForm({ initial: formData, saving: false, fieldErrors: {}, onSubmit: data => submitted = data });
  const html = renderToStaticMarkup(tree);
  for (const label of ["Company Name", "Type of Company", "Address of Company", "Company Phone Number", "Industry Supervisor", "Internship Mode"]) assert.ok(html.includes(label));
  assert.doesNotMatch(html, forbidden);
  let prevented = false; tree.props.onSubmit({ preventDefault() { prevented = true; } });
  assert.equal(prevented, true); assert.equal(submitted.companyAddress, details.companyAddress); assert.equal(Object.hasOwn(submitted, "workplace"), false);
});

function hooks(values) {
  let index = 0;
  return { useEffect() {}, useState(initial) { const i = index++; return [i in values ? values[i] : initial, () => {}]; } };
}
const children = ({ children }) => children;
const empty = () => null;
const shared = { "next/link": { default: ({ children, ...props }) => React.createElement("a", props, children) },
  "next/navigation": { useParams: () => ({ id: "7" }) }, "@/lib/api": {},
  "@/components/AppFrame": { AppFrame: children }, "@/components/ProtectedRoute": { ProtectedRoute: children },
  "@/components/InternshipDetailsView": view, "@/lib/internship-details": internship };

test("student internship view preserves company information without loading workplace configuration", () => {
  const { default: Page } = load("app/student/internship/details/page.tsx", { ...shared,
    react: hooks({ 0: { workflowStatus: "INTERNSHIP_DETAILS_COMPLETE" }, 1: details, 2: false }),
    "@/components/InternshipDetailsForm": { InternshipDetailsForm: empty }
  });
  const html = renderToStaticMarkup(React.createElement(Page));
  assert.match(html, /Existing company|Existing postal address/); assert.doesNotMatch(html, forbidden);
});
test("admin student view preserves company and hybrid configuration without approval or correction panels", () => {
  const { default: Page } = load("app/admin/students/[id]/page.tsx", { ...shared,
    react: hooks({ 0: { id: 7, internshipDetails: details }, 1: false }),
    "@/components/AdminProfessionalProfile": { AdminProfessionalProfile: empty },
    "@/components/HybridScheduleManager": { HybridScheduleManager: empty },
    "@/components/InternshipOnboardingDetails": { InternshipOnboardingDetails: empty },
    "@/components/ProfileDetails": { ProfileDetails: empty }, "@/components/StatusBadge": { StatusBadge: empty }
  });
  const html = renderToStaticMarkup(React.createElement(Page));
  assert.match(html, /Existing company|Existing postal address/); assert.doesNotMatch(html, forbidden);
});
test("mentor student view retains assigned student profile without workplace panels", () => {
  const { default: Page } = load("app/mentor/students/[id]/page.tsx", { ...shared,
    react: hooks({ 0: { studentName: "Assigned student", confirmedSkills: [] } })
  });
  const html = renderToStaticMarkup(React.createElement(Page));
  assert.match(html, /Assigned student|Professional Profile/); assert.doesNotMatch(html, forbidden);
});
