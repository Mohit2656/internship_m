const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const ts = require("typescript");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");

function load(file, imports = {}) {
  const module = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname, "../components/" + file + ".tsx"), "utf8");
  new Function(
    "require",
    "module",
    "exports",
    ts.transpileModule(source, {
      compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
    }).outputText
  )(
    (name) => (Object.hasOwn(imports, name) ? imports[name] : require(name)),
    module,
    module.exports
  );
  return module.exports;
}

const maps = load("LocationMapLink");
const preview = (props) => React.createElement("div", { "data-preview": `${props.position.latitude},${props.position.longitude}` }, "Leaflet preview");
const picker = () => React.createElement("div", null, "Correction picker");

const location1 = {
  id: 101,
  internshipId: 1,
  latitude: 21.125432,
  longitude: 79.087654,
  geofenceRadiusMeters: 200,
  revision: 1,
  status: "APPROVED",
  workplaceName: "Nagpur Office",
  workplaceAddress: "Nagpur Tech Park, Wardha Road",
  placeId: null,
  companyName: "Company A",
  confirmedAt: "2026-10-01T10:00:00Z",
  confirmedBy: 5,
  mentorDecision: "APPROVE",
  mentorReviewerId: 9,
  mentorReviewedAt: "2026-10-01T10:00:00Z",
  mentorRemarks: "Approved branch",
  iiicDecision: null,
  iiicRemarks: null,
  coordinatesLocked: true,
};

const location2 = {
  id: 102,
  internshipId: 2,
  latitude: 19.076123,
  longitude: 72.877456,
  geofenceRadiusMeters: 200,
  revision: 2,
  status: "PENDING_MENTOR_APPROVAL",
  workplaceName: "Mumbai Hub",
  workplaceAddress: "Bandra Kurla Complex",
  placeId: null,
  companyName: "Company B",
  confirmedAt: null,
  confirmedBy: null,
  mentorDecision: null,
  mentorReviewerId: null,
  mentorReviewedAt: null,
  mentorRemarks: null,
  iiicDecision: null,
  iiicRemarks: null,
  coordinatesLocked: false,
};

function renderManager(props, locationValue = location1) {
  let cursor = 0;
  const seed = {
    3: locationValue,
    4: String(locationValue?.latitude ?? ""),
    5: String(locationValue?.longitude ?? ""),
    6: String(locationValue?.geofenceRadiusMeters ?? "200"),
    7: false,
  };
  const hooks = {
    useState(initial) {
      const i = cursor++;
      return [i in seed ? seed[i] : initial, () => {}];
    },
    useRef(initial) {
      return { current: initial };
    },
    useEffect() {},
  };
  const { InternshipLocationManager } = load("InternshipLocationManager", {
    react: hooks,
    "next/dynamic": { default: () => preview },
    "next/link": { default: ({ children, ...rest }) => React.createElement("a", rest, children) },
    "@/lib/api": { api: {} },
    "@/components/OpenStreetMapWorkplacePicker": { OpenStreetMapWorkplacePicker: picker },
    "@/components/LocationMapLink": maps,
  });

  return renderToStaticMarkup(InternshipLocationManager(props));
}

test("single internship student view: renders workplace without tabs and without empty section", () => {
  const html = renderManager({
    scope: "student",
    internships: [{ id: 1, internshipNumber: 1, companyName: "Company A", status: "Active" }],
  }, location1);

  // Tabs must NOT be displayed
  assert.doesNotMatch(html, /role="tablist"/);
  assert.doesNotMatch(html, /Internship 2/);

  // Read-only values displayed
  assert.match(html, /Company A/);
  assert.match(html, /Nagpur Office/);
  assert.match(html, /21\.125432/);
  assert.match(html, /79\.087654/);
  assert.match(html, /200 meters/);
  assert.doesNotMatch(html, /100 meters/);
  assert.match(html, />Approved</);
  assert.match(html, /student coordinates are locked/);

  // No student-editable inputs
  assert.doesNotMatch(html, /<input/);
});

test("two internships student view: renders two tabs [Internship 1] and [Internship 2]", () => {
  const html = renderManager({
    scope: "student",
    internships: [
      { id: 1, internshipNumber: 1, companyName: "Company A", status: "Active" },
      { id: 2, internshipNumber: 2, companyName: "Company B", status: "Upcoming" },
    ],
  }, location1);

  // Tabs MUST be displayed
  assert.match(html, /role="tablist"/);
  assert.match(html, /Internship 1/);
  assert.match(html, /Internship 2/);
  assert.match(html, /Upcoming/);
  assert.doesNotMatch(html, /Active.*Upcoming.*Active/); // Upcoming is not shown as active
});

test("internship 2 selected displays only Internship 2 workplace coordinates", () => {
  const html = renderManager({
    scope: "student",
    initialInternshipId: 2,
    internships: [
      { id: 1, internshipNumber: 1, companyName: "Company A", status: "Active" },
      { id: 2, internshipNumber: 2, companyName: "Company B", status: "Upcoming" },
    ],
  }, location2);

  // Shows Internship 2 details
  assert.match(html, /Company B/);
  assert.match(html, /Mumbai Hub/);
  assert.match(html, /19\.076123/);
  assert.match(html, /72\.877456/);
  assert.match(html, /200 meters/);
  assert.match(html, /Pending mentor approval/);
  assert.match(html, /Waiting for the assigned mentor/);

  // Does NOT leak Internship 1 coordinates
  assert.doesNotMatch(html, /21\.125432/);
  assert.doesNotMatch(html, /79\.087654/);
});

test("missing coordinates display 'Workplace location not submitted' and link to internship details", () => {
  const html = renderManager({
    scope: "student",
    internships: [
      { id: 1, internshipNumber: 1, companyName: "Company A", status: "Active" },
      { id: 2, internshipNumber: 2, companyName: "Company B", status: "Upcoming" },
    ],
  }, null);

  assert.match(html, /Workplace location not submitted/);
  assert.match(html, /Submit workplace location/);
  assert.match(html, /href="\/student\/internship\/details"/);
  assert.match(html, /Not submitted/);
  assert.doesNotMatch(html, /<input/);
});
