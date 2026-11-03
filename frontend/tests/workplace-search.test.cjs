const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const ts = require("typescript");
require("./register-tsx.cjs");

function load(relative, imports = {}) {
  const module = { exports: {} };
  const compiled = ts.transpileModule(fs.readFileSync(path.join(__dirname, "..", relative), "utf8"), {
    compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, esModuleInterop: true },
  }).outputText;
  new Function("require", "module", "exports", compiled)(name => Object.hasOwn(imports, name) ? imports[name] : require(name), module, module.exports);
  return module.exports;
}

const location = load("lib/workplace-location.ts");
const internship = load("lib/internship-details.ts");
const tick = () => new Promise(resolve => setImmediate(resolve));
const nodes = tree => !tree || typeof tree !== "object" ? [] : [tree, ...[tree.props?.children].flat(Infinity).flatMap(nodes)];
const text = tree => tree == null || typeof tree === "boolean" ? "" : typeof tree !== "object" ? String(tree) : [tree.props?.children].flat(Infinity).map(text).join("");
function button(tree, label) {
  const found = nodes(tree).find(node => node.type === "button" && text(node) === label);
  assert.ok(found, `Expected button '${label}'`);
  return found;
}
const input = tree => nodes(tree).find(node => node.type === "input");

// Runs real component handlers and effect cleanup with deterministic hook state.
function hooksHarness() {
  const states = [], refs = [], dependencies = [], cleanups = [], effects = [];
  let stateIndex = 0, refIndex = 0, effectIndex = 0;
  return {
    hooks: {
      useState(initial) {
        const i = stateIndex++;
        if (!(i in states)) states[i] = typeof initial === "function" ? initial() : initial;
        return [states[i], value => { states[i] = typeof value === "function" ? value(states[i]) : value; }];
      },
      useRef(initial) { const i = refIndex++; return refs[i] ??= { current: initial }; },
      useEffect(effect, deps) {
        const i = effectIndex++;
        if (!dependencies[i] || !deps || deps.some((value, j) => !Object.is(value, dependencies[i][j]))) {
          dependencies[i] = deps;
          effects.push(() => { cleanups[i]?.(); cleanups[i] = effect(); });
        }
      },
    },
    render(component, props) {
      stateIndex = 0; refIndex = 0; effectIndex = 0;
      const tree = component(props);
      effects.splice(0).forEach(effect => effect());
      return tree;
    },
    close() { cleanups.forEach(cleanup => cleanup?.()); },
  };
}

const branch = { name: "Example workplace", address: "10 Example Road, Nagpur, Maharashtra, India", latitude: 21.1458, longitude: 79.0882 };
function searchHarness(initial = {}) {
  const h = hooksHarness(), requests = [], selections = [];
  const props = { onSelect: result => selections.push(result), ...initial };
  const { WorkplaceLocationSearch } = load("components/WorkplaceLocationSearch.tsx", {
    react: h.hooks,
    "@/lib/workplace-location": location,
    "@/lib/api": { api: { get(url, options) {
      let resolve, reject;
      const promise = new Promise((done, fail) => { resolve = done; reject = fail; });
      requests.push({ url, options, resolve, reject });
      return promise;
    } } },
  });
  return { props, requests, selections, render: () => h.render(WorkplaceLocationSearch, props), close: h.close };
}
function enterQuery(h, query) {
  input(h.render()).props.onChange({ target: { value: query } });
  return h.render();
}
function search(h, query = "Example workplace") {
  const tree = enterQuery(h, query);
  button(tree, "Search").props.onClick();
  return h.render();
}

test("workplace search is explicitly triggered, trims its query and never submits the outer form", async () => {
  const h = searchHarness();
  let tree = h.render();
  assert.match(text(tree), /Search for a workplace/);
  assert.equal(input(tree).props.maxLength, 200);
  assert.equal(button(tree, "Search").props.type, "button");
  assert.equal(button(tree, "Search").props.disabled, true);
  tree = enterQuery(h, "  Example workplace  ");
  assert.equal(h.requests.length, 0);
  let prevented = false;
  input(tree).props.onKeyDown({ key: "Enter", nativeEvent: { isComposing: false }, preventDefault() { prevented = true; }, stopPropagation() {} });
  assert.equal(prevented, true);
  assert.equal(h.requests.length, 1);
  assert.equal(h.requests[0].url, "/workplace-locations/search");
  assert.deepEqual(h.requests[0].options.params, { q: "Example workplace" });
  assert.ok(h.requests[0].options.signal instanceof AbortSignal);
  tree = h.render();
  assert.equal(button(tree, "Searching…").props.disabled, true);
  h.requests[0].resolve({ data: [branch] }); await tick();
  tree = h.render();
  assert.match(text(tree), /Example workplace/);
  assert.match(text(tree), /10 Example Road/);
  assert.match(text(tree), /Approximate address/);
  assert.deepEqual(h.selections, []);
  const resultButton = nodes(tree).find(node => node.type === "button" && text(node).includes(branch.address));
  assert.ok(resultButton);
  assert.equal(resultButton.props.type, "button");
  resultButton.props.onClick();
  assert.deepEqual(h.selections, [branch]);
  h.close();
});

test("search sends optional map bias and labels every additive Photon match quality", async () => {
  const focus = { latitude: 21.1498, longitude: 79.082 };
  const h = searchHarness({ focus });
  search(h, "Virtual Galaxy Nagpur");
  assert.deepEqual(h.requests[0].options.params, { q: "Virtual Galaxy Nagpur", lat: focus.latitude, lon: focus.longitude });
  h.requests[0].resolve({ data: [
    { ...branch, matchQuality: "EXACT_BUSINESS" },
    { ...branch, name: "Plot 26", matchQuality: "APPROXIMATE_ADDRESS" },
    { ...branch, name: "Ring Road", matchQuality: "STREET_OR_POSTCODE" },
    { ...branch, name: "Nagpur", matchQuality: "LOCALITY_OR_CITY" },
  ] });
  await tick();
  const content = text(h.render());
  for (const label of ["Provider-listed business", "Approximate address", "Street or postcode match", "City or locality match"]) assert.match(content, new RegExp(label));
  assert.match(content, /not IMS verified/i);
  assert.match(content, /low precision/i);
  h.close();
});

test("short, whitespace and overlong workplace queries cannot reach the API", () => {
  const h = searchHarness();
  for (const query of ["", "  ", "ab", " ab ", "x".repeat(201)]) {
    const tree = enterQuery(h, query);
    if (query.trim().length < 3) assert.equal(button(tree, "Search").props.disabled, true);
    let prevented = false;
    input(tree).props.onKeyDown({ key: "Enter", nativeEvent: { isComposing: false }, preventDefault() { prevented = true; }, stopPropagation() {} });
    assert.equal(prevented, true);
  }
  assert.equal(h.requests.length, 0);
  h.close();
});

test("normal typing and composing Enter do not trigger workplace search", () => {
  const h = searchHarness(); const tree = enterQuery(h, "Example workplace");
  input(tree).props.onKeyDown({ key: "e" });
  let prevented = false, stopped = false;
  input(tree).props.onKeyDown({ key: "Enter", nativeEvent: { isComposing: true }, preventDefault() { prevented = true; }, stopPropagation() { stopped = true; } });
  assert.equal(prevented, false); assert.equal(stopped, false);
  assert.equal(h.requests.length, 0);
  h.close();
});

test("search failures and empty results give a useful retry path", async () => {
  const h = searchHarness();
  search(h);
  h.requests[0].reject({ response: { data: { message: "Workplace search is temporarily unavailable. Please try again." } } }); await tick();
  let tree = h.render();
  assert.ok(nodes(tree).some(node => node.props?.role === "alert"));
  assert.equal(button(tree, "Search").props.disabled, false);
  button(tree, "Search").props.onClick();
  h.requests[1].resolve({ data: [] }); await tick();
  tree = h.render();
  assert.match(text(tree), /no .*found|no results/i);
  assert.match(text(tree), /locate the real office manually/i);
  assert.deepEqual(h.selections, []);
  h.close();
});

test("rate limits and malformed API responses show recoverable search errors", async () => {
  const h = searchHarness(); search(h);
  h.requests[0].reject({ response: { status: 429 } }); await tick();
  let tree = h.render();
  assert.ok(nodes(tree).some(node => node.props?.role === "alert" && /wait|busy/i.test(text(node))));
  assert.match(text(tree), /manually on the map/i);
  button(tree, "Search").props.onClick();
  h.requests[1].resolve({ data: { address: "Not an array of workplaces" } }); await tick();
  tree = h.render();
  assert.ok(nodes(tree).some(node => node.props?.role === "alert" && /unavailable|retry/i.test(text(node))));
  assert.deepEqual(h.selections, []);
  h.close();
});

test("editing the query cancels a search and ignores its late response", async () => {
  const h = searchHarness();
  search(h, "First branch");
  let tree = enterQuery(h, "Second branch");
  assert.equal(h.requests[0].options.signal.aborted, true);
  button(tree, "Search").props.onClick();
  const second = { ...branch, name: "Second branch", address: "Second branch address", latitude: 19.076, longitude: 72.8777 };
  h.requests[1].resolve({ data: [second] }); await tick();
  h.requests[0].resolve({ data: [branch] }); await tick();
  tree = h.render();
  assert.match(text(tree), /Second branch address/);
  assert.doesNotMatch(text(tree), /10 Example Road/);
  assert.equal(nodes(tree).filter(node => node.props?.role === "alert").length, 0);
  h.close();
});

test("clearing an old query removes results until a fresh explicit search", async () => {
  const h = searchHarness();
  search(h); h.requests[0].resolve({ data: [branch] }); await tick();
  assert.match(text(h.render()), /10 Example Road/);
  enterQuery(h, "Another branch");
  assert.doesNotMatch(text(h.render()), /10 Example Road/);
  assert.equal(h.requests.length, 1);
  h.close();
});

test("disabling or unmounting search cancels pending requests and ignores late errors", async () => {
  for (const action of ["disable", "unmount"]) {
    const h = searchHarness(); search(h);
    if (action === "disable") { h.props.disabled = true; h.render(); } else h.close();
    assert.equal(h.requests[0].options.signal.aborted, true);
    h.requests[0].reject(new Error("Late provider failure")); await tick();
    if (action === "disable") {
      const tree = h.render();
      assert.equal(input(tree).props.disabled, true);
      assert.equal(nodes(tree).filter(node => node.props?.role === "alert").length, 0);
      h.close();
    }
  }
});

test("malformed search results cannot fabricate a selectable workplace", async () => {
  const h = searchHarness(); search(h);
  h.requests[0].resolve({ data: [
    null, {}, { ...branch, latitude: NaN }, { ...branch, longitude: Infinity },
    { ...branch, latitude: 91 }, { ...branch, longitude: -181 },
    { ...branch, latitude: "21.1458" }, { ...branch, address: " " },
    { ...branch, address: 123 }, { ...branch, name: 123 }, branch,
  ] }); await tick();
  const tree = h.render();
  const results = nodes(tree).filter(node => node.type === "button" && text(node).includes(branch.address));
  assert.equal(results.length, 1);
  results[0].props.onClick();
  assert.deepEqual(h.selections, [branch]);
  h.close();
});

test("address-only places and zero coordinates remain selectable and results are bounded", async () => {
  const h = searchHarness(); search(h);
  const origin = { name: null, address: "Address-only search result", latitude: 0, longitude: 0 };
  h.requests[0].resolve({ data: [origin, ...Array.from({ length: 8 }, (_, i) => ({ ...branch, name: `Branch ${i}`, address: `Branch ${i} address` }))] }); await tick();
  const tree = h.render();
  const choices = nodes(tree).filter(node => node.type === "li");
  assert.equal(choices.length, 5);
  nodes(tree).find(node => node.type === "button" && text(node).includes(origin.address)).props.onClick();
  assert.deepEqual(h.selections, [origin]);
  h.close();
});

const saved = { name: "Saved Google branch", placeId: "legacy-place-id", address: "Saved branch address", latitude: 21, longitude: 79 };
function pickerHarness(initial = {}) {
  const h = hooksHarness(), selections = [], pending = [], addresses = [];
  const props = { value: saved, name: "Company", onSelect(value) { selections.push(value); props.value = value; }, onPendingChange(value) { pending.push(value); }, ...initial };
  const { OpenStreetMapWorkplacePicker } = load("components/OpenStreetMapWorkplacePicker.tsx", {
    react: h.hooks,
    "next/dynamic": () => "WorkplaceMap",
    "@/lib/workplace-location": location,
  });
  return { props, selections, pending, addresses, render: () => h.render(OpenStreetMapWorkplacePicker, props), coordinateInputs: tree => nodes(tree).filter(node => node.type === "input" && node.props.inputMode === "decimal"), close: h.close };
}

test("manual coordinates create an unconfirmed draft with an external map link before explicit confirmation", () => {
  const h = pickerHarness(); let tree = h.render();
  let coordinates = h.coordinateInputs(tree); coordinates[0].props.onChange({ target: { value: String(branch.latitude) } }); coordinates[1].props.onChange({ target: { value: String(branch.longitude) } });
  tree = h.render(); button(tree, "Use coordinates").props.onClick();
  assert.equal(h.selections.at(-1), null);
  tree = h.render();
  const map = nodes(tree).find(node => node.props.label === "Open in Google Maps");
  assert.equal(map.props.latitude, branch.latitude);
  assert.equal(map.props.longitude, branch.longitude);
  assert.equal(nodes(tree).find(node => node.type === "input" && !node.props.inputMode).props.value, saved.address);
  assert.match(text(tree), /Student-entered coordinates/);
  assert.equal(h.pending.at(-1), true);
  button(tree, "Confirm workplace location").props.onClick();
  assert.equal(h.selections.at(-1).latitude, branch.latitude);
  assert.equal(h.selections.at(-1).longitude, branch.longitude);
  assert.equal(h.selections.at(-1).address, saved.address);
  assert.equal(h.selections.at(-1).placeId, null);
  h.close();
});

test("controlled company addresses are never overwritten by manual coordinate preview", () => {
  const h = pickerHarness({ address: saved.address });
  h.props.onAddressChange = address => { h.addresses.push(address); h.props.address = address; };
  let tree = h.render(); let coordinates = h.coordinateInputs(tree); coordinates[0].props.onChange({ target: { value: String(branch.latitude) } }); coordinates[1].props.onChange({ target: { value: String(branch.longitude) } });
  tree = h.render(); button(tree, "Use coordinates").props.onClick();
  assert.deepEqual(h.addresses, []);
  assert.equal(h.selections.at(-1), null);
  tree = h.render();
  assert.equal(h.coordinateInputs(tree).length, 2);
  const map = nodes(tree).find(node => node.props.label === "Open in Google Maps");
  assert.equal(map.props.latitude, branch.latitude);
  assert.equal(map.props.longitude, branch.longitude);
  button(tree, "Confirm workplace location").props.onClick();
  assert.equal(h.selections.at(-1).address, saved.address);
  assert.equal(h.selections.at(-1).latitude, branch.latitude);
  h.close();
});

test("manual input respects disabled pickers and preserves unchanged saved metadata", () => {
  const h = pickerHarness(); let tree = h.render();
  button(tree, "Confirm workplace location").props.onClick();
  assert.strictEqual(h.selections.at(-1), saved);
  h.props.disabled = true; tree = h.render();
  assert.ok(h.coordinateInputs(tree).every(field => field.props.disabled === undefined));
  button(tree, "Use coordinates").props.onClick();
  assert.equal(h.selections.length, 1);
  h.close();
});

test("editing manual coordinates after confirmation invalidates the saved workplace and requires reconfirmation", () => {
  const h = pickerHarness(); let tree = h.render();
  button(tree, "Confirm workplace location").props.onClick();
  assert.strictEqual(h.selections.at(-1), saved);
  h.coordinateInputs(tree)[0].props.onChange({ target: { value: "20" } });
  assert.equal(h.selections.at(-1), null);
  tree = h.render();
  assert.equal(button(tree, "Confirm workplace location").props.disabled, true);
  assert.match(text(tree), /Enter the workplace coordinates/i);
  h.close();
});

test("picker displays the applicable server-returned geofence radius, not GPS accuracy", () => {
  const h = pickerHarness({ geofenceRadiusMeters: 175 });
  const tree = h.render();
  assert.match(text(tree), /Geofence radius:\s*175 metres/);
  assert.doesNotMatch(text(tree), /200 metres/);
  h.close();
});

test("internship form keeps the searched company address and confirmed location in its submission", () => {
  const h = hooksHarness(), submissions = [];
  function Picker() { return null; }
  const { InternshipDetailsForm } = load("components/InternshipDetailsForm.tsx", {
    react: h.hooks,
    "@/lib/internship-details": internship,
    "@/components/OpenStreetMapWorkplacePicker": { OpenStreetMapWorkplacePicker: Picker },
  });
  const props = { initial: { companyName: "Company", companyAddress: saved.address, startDate: "2026-11-01", endDate: "2026-12-30", internshipMode: "OFFICE_REPORTING", workplace: saved }, saving: false, fieldErrors: {}, onSubmit: data => submissions.push(data) };
  let tree = h.render(InternshipDetailsForm, props);
  let picker = nodes(tree).find(node => node.type === Picker);
  picker.props.onSelect(null); picker.props.onAddressChange(branch.address); picker.props.onPendingChange(true);
  tree = h.render(InternshipDetailsForm, props);
  let prevented = false;
  tree.props.onSubmit({ preventDefault() { prevented = true; } });
  assert.equal(prevented, true); assert.equal(submissions.length, 0);
  picker = nodes(tree).find(node => node.type === Picker);
  assert.equal(picker.props.address, branch.address);
  picker.props.onSelect({ ...branch, placeId: null }); picker.props.onPendingChange(false);
  tree = h.render(InternshipDetailsForm, props);
  tree.props.onSubmit({ preventDefault() {} });
  assert.equal(submissions.length, 1);
  assert.equal(submissions[0].companyAddress, branch.address);
  assert.equal(submissions[0].totalWeeks, "9");
  assert.equal(submissions[0].workplace.latitude, branch.latitude);
  assert.equal(submissions[0].workplace.longitude, branch.longitude);
  h.close();
});

test("mentor-approved coordinates disable only the workplace picker and explain the permanent student lock", () => {
  const h = hooksHarness(), submissions = [];
  function Picker() { return null; }
  const { InternshipDetailsForm } = load("components/InternshipDetailsForm.tsx", {
    react: h.hooks,
    "@/lib/internship-details": internship,
    "@/components/OpenStreetMapWorkplacePicker": { OpenStreetMapWorkplacePicker: Picker },
  });
  const props = { initial: { companyName: "Company", companyAddress: saved.address, startDate: "2026-11-01", endDate: "2026-12-30", internshipMode: "OFFICE_REPORTING", workplace: saved }, saving: false, fieldErrors: {}, workplaceCoordinatesLocked: true, onSubmit: data => submissions.push(data) };
  let tree = h.render(InternshipDetailsForm, props);
  const picker = nodes(tree).find(node => node.type === Picker);
  assert.equal(picker.props.disabled, true);
  assert.match(text(tree), /Location approved by mentor — coordinates locked/);
  const companyName = nodes(tree).find(node => node.type === "input" && node.props.value === "Company");
  assert.equal(companyName.props.disabled, undefined);
  companyName.props.onChange({ target: { value: "Renamed Company" } });
  tree = h.render(InternshipDetailsForm, props);
  tree.props.onSubmit({ preventDefault() {} });
  assert.equal(submissions.length, 1);
  assert.equal(submissions[0].companyName, "Renamed Company");
  assert.strictEqual(submissions[0].workplace, saved);
  h.close();
});
