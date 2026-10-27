const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const ts = require("typescript");

const tick = () => new Promise(resolve => setImmediate(resolve));
function load(file, imports) {
  const module = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname, "../components", file + ".tsx"), "utf8");
  const compiled = ts.transpileModule(source, { compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, esModuleInterop: true } }).outputText;
  new Function("require", "module", "exports", compiled)(name => imports[name] || require(name), module, module.exports);
  return module.exports;
}
async function mapHarness(geocode) {
  const effects = [], selections = [], messages = [], widgets = [], maps = [], markers = [];
  let stateIndex = 0;
  const hosts = [{}, { replaceChildren() {} }];
  let refIndex = 0;
  const hooks = {
    useState(initial) { const i = stateIndex++; return [i === 1 ? true : initial, value => messages.push(value)]; },
    useRef(initial) { const i = refIndex++; return { current: i < 2 ? hosts[i] : initial }; },
    useEffect(effect) { effects.push(effect); }
  };
  const oldWindow = global.window, oldKey = process.env.NEXT_PUBLIC_GOOGLE_MAPS_API_KEY;
  process.env.NEXT_PUBLIC_GOOGLE_MAPS_API_KEY = "test-key";
  global.window = { google: { maps: { async importLibrary(name) {
    if (name === "places") return { PlaceAutocompleteElement: class { constructor() { this.events = {}; widgets.push(this); } setAttribute() {} addEventListener(name, fn) { this.events[name] = fn; } } };
    if (name === "maps") return { Map: class { constructor(host, options) { this.options = options; this.events = {}; maps.push(this); } addListener(name, fn) { this.events[name] = fn; return { remove: () => delete this.events[name] }; } } };
    if (name === "marker") return { AdvancedMarkerElement: class { constructor(options) { Object.assign(this, options); this.events = {}; markers.push(this); } addEventListener(name, fn) { this.events[name] = fn; } removeEventListener(name) { delete this.events[name]; } } };
    if (name === "geocoding") return { Geocoder: class { geocode(options) { return geocode(options); } } };
    throw new Error("Unexpected library");
  } } } };
  const { WorkplacePlaceSelector } = load("WorkplacePlaceSelector", { react: hooks, "next/script": () => null });
  WorkplacePlaceSelector({ value: { name: "Actual branch", placeId: "original-place", address: "Original address", latitude: 21.125, longitude: 79.0875 }, onSelect: value => selections.push(value) });
  const cleanup = effects.map(effect => effect());
  await tick();
  return { selections, messages, widgets, maps, markers, close() { cleanup.forEach(fn => fn?.()); global.window = oldWindow; if (oldKey === undefined) delete process.env.NEXT_PUBLIC_GOOGLE_MAPS_API_KEY; else process.env.NEXT_PUBLIC_GOOGLE_MAPS_API_KEY = oldKey; } };
}
test("map is centered on actual selection; pin drag resolves its new address and clears old place evidence", async () => {
  let resolve;
  const h = await mapHarness(() => new Promise(done => { resolve = done; }));
  try {
    assert.deepEqual(h.maps[0].options.center, { lat: 21.125, lng: 79.0875 });
    assert.equal(h.markers[0].gmpDraggable, true);
    h.markers[0].position = { lat: 21.126, lng: 79.088 };
    h.markers[0].events["gmp-dragend"]();
    assert.deepEqual(h.selections[0], { name: "Actual branch", placeId: null, address: "", latitude: 21.126, longitude: 79.088 });
    resolve({ results: [{ formatted_address: "Resolved entrance address", place_id: "actual-pin-place" }] }); await tick();
    assert.equal(h.selections[1].address, "Resolved entrance address");
    assert.equal(h.selections[1].placeId, "actual-pin-place");
  } finally { h.close(); }
});
test("failed map lookup leaves selection incomplete instead of reusing an old address", async () => {
  const h = await mapHarness(async () => { throw new Error("Provider unavailable"); });
  try {
    h.maps[0].events.click({ latLng: { lat: () => 19.076, lng: () => 72.8777 } }); await tick();
    assert.equal(h.selections.length, 1); assert.equal(h.selections[0].address, "");
    assert.equal(h.selections[0].placeId, null); assert.match(h.messages.at(-1), /could not be retrieved/);
  } finally { h.close(); }
});
test("a late pin lookup cannot replace a newer selected company branch", async () => {
  let reject;
  const h = await mapHarness(() => new Promise((resolve, fail) => { reject = fail; }));
  try {
    h.maps[0].events.click({ latLng: { lat: () => 21.126, lng: () => 79.088 } });
    const place = { id: "new-branch", displayName: "Branch B", formattedAddress: "Branch B address", location: { lat: () => 19.076, lng: () => 72.8777 }, async fetchFields() {} };
    await h.widgets[0].events["gmp-select"]({ placePrediction: { toPlace: () => place } });
    reject(new Error("Old pin request failed")); await tick();
    assert.equal(h.selections.length, 2); assert.equal(h.selections.at(-1).placeId, "new-branch");
    assert.equal(h.messages.at(-1), "");
  } finally { h.close(); }
});
test("Places selection fetches actual branch fields and rejects incomplete provider results", async () => {
  const h = await mapHarness(async () => ({ results: [] }));
  try {
    let fields;
    const place = { id: "branch-place", displayName: "Branch B", formattedAddress: "Branch B address", location: { lat: () => 19.076, lng: () => 72.8777 }, async fetchFields(options) { fields = options.fields; } };
    await h.widgets[0].events["gmp-select"]({ placePrediction: { toPlace: () => place } });
    assert.deepEqual(fields, ["id", "displayName", "formattedAddress", "location"]);
    assert.equal(h.selections[0].latitude, 19.076); assert.equal(h.selections[0].placeId, "branch-place");
    await h.widgets[0].events["gmp-select"]({ placePrediction: { toPlace: () => ({ ...place, location: undefined }) } });
    assert.equal(h.selections.length, 1); assert.match(h.messages.at(-1), /valid map coordinates/);
  } finally { h.close(); }
});
function nodes(tree) { if (!tree || typeof tree !== "object") return []; return [tree, ...[tree.props?.children].flat(Infinity).flatMap(nodes)]; }
function reviewHarness(scope, fail) {
  const states = { 2: "Incorrect branch selected", 3: { revision: 7, latitude: 21, longitude: 79, geofenceRadiusMeters: 200, workplaceAddress: "Actual branch address", status: "PENDING", mentorDecision: null, iiicDecision: "APPROVE" }, 6: "200", 7: false };
  let index = 0; const calls = [];
  const hooks = { useEffect() {}, useRef(initial) { return { current: initial }; }, useState(initial) { const i = index++; return [i in states ? states[i] : initial, value => { states[i] = value; }]; } };
  const { InternshipLocationManager } = load("InternshipLocationManager", { react: hooks, "@/lib/api": { api: { async post(url, body) { calls.push({ url, body }); if (fail) throw { response: { data: { message: "Workplace changed. Refresh." } } }; return { data: { ...states[3], status: "APPROVED", mentorDecision: "APPROVE" } }; } } }, "@/components/OpenStreetMapWorkplacePicker": { OpenStreetMapWorkplacePicker: () => null }, "@/components/LocationMapLink": { LocationMapLink: () => null } });
  return { states, calls, tree: InternshipLocationManager({ studentId: 42, scope }) };
}
test("mentor review posts selected revision through mentor endpoint and updates the panel", async () => {
  const h = reviewHarness("mentor"); const button = nodes(h.tree).find(n => n.type === "button" && n.props.children === "Approve workplace");
  button.props.onClick(); await tick();
  assert.deepEqual(h.calls[0], { url: "/mentor/students/42/internship/location/review", body: { revision: 7, decision: "APPROVE", remarks: "Incorrect branch selected" } });
  assert.equal(h.states[3].status, "APPROVED");
});
test("IIIC rejection uses its own endpoint and shows backend review errors", async () => {
  const h = reviewHarness("admin", true); const button = nodes(h.tree).find(n => n.type === "button" && n.props.children === "Reject workplace");
  button.props.onClick(); await tick();
  assert.equal(h.calls[0].url, "/admin/students/42/internship/location/review"); assert.equal(h.calls[0].body.decision, "REJECT");
  assert.equal(h.states[10], "Workplace changed. Refresh."); assert.equal(h.states[3].iiicDecision, "APPROVE");
});
