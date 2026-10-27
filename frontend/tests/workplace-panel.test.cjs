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
  new Function("require", "module", "exports", ts.transpileModule(source, {
    compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText)(name => Object.hasOwn(imports, name) ? imports[name] : require(name), module, module.exports);
  return module.exports;
}

const maps = load("LocationMapLink");
const picker = () => React.createElement("div", { "data-workplace-picker": "openstreetmap" }, "OpenStreetMap workplace picker");
const location = {
  id: 1, latitude: 21.125, longitude: 79.0875, geofenceRadiusMeters: 200, revision: 4, status: "APPROVED",
  workplaceName: "Actual registered branch", workplaceAddress: "Registered branch address", placeId: null,
  companyName: "Company", confirmedAt: null, mentorDecision: "APPROVE", iiicDecision: "APPROVE",
};

function panel(scope, value) {
  let cursor = 0;
  const seed = { 3: value, 4: value ? String(value.latitude) : "", 5: value ? String(value.longitude) : "", 6: "200", 7: false };
  const hooks = { useState(initial) { const i = cursor++; return [i in seed ? seed[i] : initial, () => {}]; }, useEffect() {} };
  const { InternshipLocationManager } = load("InternshipLocationManager", {
    react: hooks, "@/lib/api": { api: {} },
    "@/components/OpenStreetMapWorkplacePicker": { OpenStreetMapWorkplacePicker: picker },
    "@/components/LocationMapLink": maps,
  });
  return renderToStaticMarkup(InternshipLocationManager({ studentId: 1, scope }));
}

function elements(tree) {
  return !tree || typeof tree !== "object" ? [] : [tree, ...[tree.props?.children].flat(Infinity).flatMap(elements)];
}
const text = tree => !tree || typeof tree === "boolean" ? "" : typeof tree !== "object" ? String(tree) : [tree.props?.children].flat(Infinity).map(text).join("");
const button = (tree, label) => {
  const result = elements(tree).find(element => element.type === "button" && text(element) === label);
  assert.ok(result, `Button '${label}' should be rendered`);
  return result;
};
const radiusInput = tree => elements(tree).find(element => element.type === "input" && element.props.max === "5000");

// Exercise hooks, handlers, and effect cleanup without a browser or live API.
function harness(initialProps = {}) {
  const states = [], refs = [], dependencies = [], cleanups = [], effects = [], requests = [];
  let stateIndex = 0, refIndex = 0, effectIndex = 0;
  const props = { studentId: 1, scope: "admin", ...initialProps };
  const hooks = {
    useState(initial) {
      const i = stateIndex++;
      if (!(i in states)) states[i] = typeof initial === "function" ? initial() : initial;
      return [states[i], next => { states[i] = typeof next === "function" ? next(states[i]) : next; }];
    },
    useRef(initial) { const i = refIndex++; return refs[i] ??= { current: initial }; },
    useEffect(effect, deps) {
      const i = effectIndex++;
      if (!dependencies[i] || !deps || deps.some((value, j) => !Object.is(value, dependencies[i][j]))) {
        dependencies[i] = deps;
        effects.push(() => { cleanups[i]?.(); cleanups[i] = effect(); });
      }
    },
  };
  const api = Object.fromEntries(["get", "post", "put"].map(method => [method, (url, body, options) => {
    let resolve, reject;
    const response = new Promise((success, failure) => { resolve = success; reject = failure; });
    requests.push({ method, url, ...(method === "get" ? { options: body } : { body, options }),
      resolve: data => resolve({ data }), reject });
    return response;
  }]));
  const { InternshipLocationManager } = load("InternshipLocationManager", {
    react: hooks, "@/lib/api": { api },
    "@/components/OpenStreetMapWorkplacePicker": { OpenStreetMapWorkplacePicker: picker },
    "@/components/LocationMapLink": maps,
  });
  const result = {
    requests,
    render(nextProps = {}) {
      Object.assign(props, nextProps);
      stateIndex = 0; refIndex = 0; effectIndex = 0;
      const tree = InternshipLocationManager(props);
      effects.splice(0).forEach(effect => effect());
      return tree;
    },
    async settle() {
      for (let i = 0; i < 8; i++) await Promise.resolve();
      return result.render();
    },
    async loaded(value = location) {
      result.render();
      requests.at(-1).resolve(value);
      return result.settle();
    },
    click(tree, label) {
      const target = button(tree, label);
      if (target.props.disabled) return false;
      target.props.onClick();
      return true;
    },
    picker(tree) {
      const component = elements(tree).find(element => element.type === picker);
      assert.ok(component, "OpenStreetMap correction picker should be rendered");
      return component.props;
    },
  };
  return result;
}

test("registered branch displays actual address, read-only coordinates and Maps link without a Google Places dependency", () => {
  const html = panel("admin", location);
  assert.match(html, /Actual registered branch/);
  assert.match(html, /Registered branch address/);
  assert.match(html, /query=21[.]125%2C79[.]0875/);
  assert.equal((html.match(/readOnly=""/g) || []).length, 2);
  assert.match(html, /Correct registered workplace/);
  assert.doesNotMatch(html, /Google Places is not configured|OpenStreetMap workplace picker/);
});

test("mentor panel is read-only and has no correction or confirmation actions", () => {
  const html = panel("mentor", location);
  assert.equal((html.match(/readOnly=""/g) || []).length, 3);
  assert.doesNotMatch(html, /Correct registered workplace|Save radius|Confirm location/);
  assert.match(html, /Approve workplace/);
  assert.match(html, /Reject workplace/);
});

test("missing and legacy workplaces remain explicit without inventing a branch", () => {
  const missing = panel("admin", null);
  assert.match(missing, /No workplace coordinates are registered/);
  assert.match(missing, />Not registered</);
  assert.doesNotMatch(missing, /maps\/search/);
  assert.match(panel("admin", { ...location, workplaceAddress: null, workplaceName: null }), /Legacy coordinates/);
});

test("pending approval identifies the missing reviewer and never invents a final approval", () => {
  const pending = { ...location, status: "PENDING" };
  const mentorMissing = panel("admin", { ...pending, mentorDecision: null });
  assert.match(mentorMissing, />Awaiting mentor</);
  assert.match(mentorMissing, /III Cell approval is recorded[.] Waiting for the assigned mentor to approve this workplace[.]/);
  const iiicMissing = panel("mentor", { ...pending, iiicDecision: null });
  assert.match(iiicMissing, />Awaiting III Cell</);
  assert.match(iiicMissing, /Mentor approval is recorded[.] Waiting for III Cell to approve this workplace[.]/);
  const bothMissing = panel("student", { ...pending, mentorDecision: null, iiicDecision: null });
  assert.match(bothMissing, />Awaiting mentor and III Cell</);
  assert.match(bothMissing, /Waiting for the assigned mentor and III Cell to approve this workplace[.]/);
  const inconsistent = panel("admin", pending);
  assert.match(inconsistent, />Approval needs review</);
  for (const html of [mentorMissing, iiicMissing, bothMissing, inconsistent]) assert.doesNotMatch(html, />Approved</);
  assert.match(panel("student", location), />Approved</);
  assert.match(panel("student", { ...location, status: "REJECTED" }), />Rejected</);
});

test("students can read independent decisions and rejection reasons but cannot review or correct", () => {
  const html = panel("student", { ...location, revision: 2, status: "REJECTED", mentorDecision: "REJECT", mentorRemarks: "Wrong branch selected", iiicDecision: "APPROVE" });
  assert.match(html, /Wrong branch selected/);
  assert.match(html, /Mentor: REJECT/);
  assert.match(html, /III Cell: APPROVE/);
  assert.doesNotMatch(html, /Approve workplace|Reject workplace|Save radius|Correct registered workplace/);
  assert.equal((html.match(/readOnly=""/g) || []).length, 3);
});

test("III Cell review posts the current revision, displays the returned pending decision, and refreshes the final status", async () => {
  const h = harness();
  let tree = await h.loaded({ ...location, status: "PENDING", mentorDecision: null, iiicDecision: null });
  assert.equal(h.requests[0].url, "/admin/students/1/internship/location");
  assert.equal(h.click(tree, "Approve workplace"), true);
  const review = h.requests.at(-1);
  assert.equal(review.method, "post");
  assert.equal(review.url, "/admin/students/1/internship/location/review");
  assert.deepEqual(review.body, { revision: 4, decision: "APPROVE", remarks: null });
  review.resolve({ ...location, status: "PENDING", mentorDecision: null, iiicDecision: "APPROVE", confirmedAt: "2026-10-27T09:00:00Z" });
  tree = await h.settle();
  assert.match(renderToStaticMarkup(tree), />Awaiting mentor</);
  assert.match(renderToStaticMarkup(tree), /III Cell: APPROVE/);
  assert.doesNotMatch(renderToStaticMarkup(tree), />Approved</);
  assert.equal(h.click(tree, "Refresh status"), true);
  h.render();
  const refresh = h.requests.at(-1);
  assert.equal(refresh.method, "get");
  assert.equal(refresh.url, "/admin/students/1/internship/location");
  refresh.resolve({ ...location, confirmedAt: "2026-10-27T09:00:00Z", mentorDecision: "APPROVE" });
  tree = await h.settle();
  assert.match(renderToStaticMarkup(tree), />Approved</);
  assert.match(renderToStaticMarkup(tree), /Mentor: APPROVE/);
});

test("unsaved radius blocks review and refresh until the server saves its new revision", async () => {
  const h = harness();
  let tree = await h.loaded();
  radiusInput(tree).props.onChange({ target: { value: "250" } });
  tree = h.render();
  assert.equal(button(tree, "Approve workplace").props.disabled, true);
  assert.equal(button(tree, "Reject workplace").props.disabled, true);
  assert.equal(button(tree, "Refresh status").props.disabled, true);
  assert.match(renderToStaticMarkup(tree), /Saving a changed radius clears both approvals and requires a new review[.]/);
  assert.equal(h.click(tree, "Save radius"), true);
  const save = h.requests.at(-1);
  assert.equal(save.method, "put");
  assert.equal(save.url, "/admin/students/1/internship/location/radius");
  assert.deepEqual(save.body, { radiusMeters: 250 });
  save.resolve({ ...location, geofenceRadiusMeters: 250, revision: 5, status: "PENDING", mentorDecision: null, iiicDecision: null });
  tree = await h.settle();
  assert.match(renderToStaticMarkup(tree), />Awaiting mentor and III Cell</);
  assert.equal(button(tree, "Approve workplace").props.disabled, false);
  assert.equal(button(tree, "Refresh status").props.disabled, false);
  assert.equal(h.click(tree, "Approve workplace"), true);
  assert.equal(h.requests.at(-1).body.revision, 5);
});

test("audited correction uses the OSM picker and refuses an unconfirmed or moved pin", async () => {
  const h = harness();
  let tree = await h.loaded();
  h.click(tree, "Correct registered workplace");
  tree = h.render();
  const initialPicker = h.picker(tree);
  assert.equal(initialPicker.value.address, location.workplaceAddress);
  assert.equal(initialPicker.value.latitude, location.latitude);
  assert.equal(initialPicker.disabled, false);
  assert.equal(typeof initialPicker.onPendingChange, "function");
  assert.doesNotMatch(renderToStaticMarkup(tree), /Google Places is not configured|Maps API key/);
  const reason = elements(tree).find(element => element.type === "textarea");
  reason.props.onChange({ target: { value: "Correct branch entrance" } });
  const chosen = { name: "Corrected branch", placeId: null, address: "Correct entrance address", latitude: 19.076, longitude: 72.8777 };
  initialPicker.onSelect(chosen);
  initialPicker.onPendingChange(true);
  tree = h.render();
  assert.equal(button(tree, "Save audited correction").props.disabled, true);
  h.picker(tree).onSelect(null);
  h.picker(tree).onPendingChange(false);
  tree = h.render();
  assert.equal(button(tree, "Save audited correction").props.disabled, true);
  assert.equal(h.requests.length, 1);
  h.picker(tree).onSelect(chosen);
  tree = h.render();
  assert.equal(h.click(tree, "Save audited correction"), true);
  const save = h.requests.at(-1);
  assert.equal(save.url, "/admin/students/1/internship/location/correction");
  assert.deepEqual(save.body, { workplace: chosen, radiusMeters: 200, reason: "Correct branch entrance" });
  save.resolve({ ...location, workplaceName: chosen.name, workplaceAddress: chosen.address, latitude: chosen.latitude,
    longitude: chosen.longitude, revision: 5, status: "PENDING", mentorDecision: null, iiicDecision: null });
  tree = await h.settle();
  const html = renderToStaticMarkup(tree);
  assert.match(html, /Corrected branch|Correct entrance address/);
  assert.match(html, />Awaiting mentor and III Cell</);
  assert.doesNotMatch(html, /OpenStreetMap workplace picker/);
});

test("an aborted older request cannot replace the newly selected student's workplace", async () => {
  const h = harness();
  h.render();
  const oldRequest = h.requests[0];
  h.render({ studentId: 2 });
  const currentRequest = h.requests.at(-1);
  assert.equal(oldRequest.options.signal.aborted, true);
  assert.equal(currentRequest.url, "/admin/students/2/internship/location");
  currentRequest.resolve({ ...location, workplaceName: "Current student's branch" });
  await h.settle();
  oldRequest.resolve({ ...location, workplaceName: "Stale student's branch" });
  const tree = await h.settle();
  const html = renderToStaticMarkup(tree);
  assert.match(html, /Current student&#x27;s branch/);
  assert.doesNotMatch(html, /Stale student/);
});

test("attendance browser GPS and workplace are independent when browser GPS is missing", () => {
  const { AttendanceLocations } = load("AttendanceLocations", { "@/components/LocationMapLink": maps });
  const html = renderToStaticMarkup(React.createElement(AttendanceLocations, {
    evidence: { latitude: null, longitude: null, expectedLatitude: 21.125, expectedLongitude: 79.0875, workplaceAddress: "Registered branch", distanceMeters: null },
  }));
  assert.match(html, /Submission location unavailable/);
  assert.doesNotMatch(html, /Registered branch|workplace map|google[.]com/);
});
