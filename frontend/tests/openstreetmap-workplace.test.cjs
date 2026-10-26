const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const ts = require("typescript");

function load(relative, imports = {}) {
  const module = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname, "..", relative), "utf8");
  const compiled = ts.transpileModule(source, { compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, esModuleInterop: true } }).outputText;
  new Function("require", "module", "exports", compiled)(name => Object.hasOwn(imports, name) ? imports[name] : require(name), module, module.exports);
  return module.exports;
}
const location = load("lib/workplace-location.ts");
const nodes = tree => !tree || typeof tree !== "object" ? [] : [tree, ...[tree.props?.children].flat(Infinity).flatMap(nodes)];
const button = (tree, label) => nodes(tree).find(node => node.type === "button" && node.props.children === label);
const workplace = { name: "Actual branch", placeId: "legacy-google-place", address: "Branch entrance address", latitude: 21.125, longitude: 79.0875 };

function pickerHarness(initial = {}) {
  const states = [], refs = [], dependencies = [], pendingEffects = [], selections = [], pending = [];
  let stateIndex = 0, refIndex = 0, effectIndex = 0;
  const hooks = {
    useState(initial) { const i = stateIndex++; if (!(i in states)) states[i] = typeof initial === "function" ? initial() : initial; return [states[i], value => { states[i] = typeof value === "function" ? value(states[i]) : value; }]; },
    useRef(initial) { const i = refIndex++; return refs[i] ??= { current: initial }; },
    useEffect(effect, deps) { const i = effectIndex++; if (!dependencies[i] || deps.some((value, j) => !Object.is(value, dependencies[i][j]))) { dependencies[i] = deps; pendingEffects.push(effect); } },
  };
  const props = { value: null, address: workplace.address, name: workplace.name, onSelect(value) { selections.push(value); props.value = value; }, onPendingChange(value) { pending.push(value); }, ...initial };
  let dynamicOptions;
  const { OpenStreetMapWorkplacePicker } = load("components/OpenStreetMapWorkplacePicker.tsx", {
    react: hooks, "next/dynamic": (loader, options) => { dynamicOptions = options; return "WorkplaceMap"; }, "@/lib/workplace-location": location,
  });
  return { props, selections, pending, dynamicOptions, render() { stateIndex = 0; refIndex = 0; effectIndex = 0; const tree = OpenStreetMapWorkplacePicker(props); pendingEffects.splice(0).forEach(effect => effect()); return tree; } };
}

test("OSM picker is client-only, starts without fabricated selection and never loads Google", () => {
  const h = pickerHarness(); const tree = h.render();
  assert.equal(h.dynamicOptions.ssr, false);
  assert.equal(nodes(tree).find(node => node.type === "WorkplaceMap").props.position, null);
  assert.equal(button(tree, "Confirm workplace location").props.disabled, true);
  assert.deepEqual(h.selections, []); assert.deepEqual(h.pending, [false]);
});

test("pin edits immediately invalidate a saved selection; confirmation emits the actual draft without a Google Places ID", () => {
  const h = pickerHarness({ value: workplace }); let tree = h.render();
  nodes(tree).find(node => node.type === "WorkplaceMap").props.onSelect({ latitude: 19.076, longitude: 72.8777 });
  assert.equal(h.selections[0], null);
  tree = h.render(); assert.equal(h.pending.at(-1), true);
  button(tree, "Confirm workplace location").props.onClick();
  assert.deepEqual(h.selections.at(-1), { name: workplace.name, placeId: null, address: workplace.address, latitude: 19.076, longitude: 72.8777 });
  h.render(); assert.equal(h.pending.at(-1), false);
});

test("confirming an unchanged saved Google workplace preserves its complete provider evidence", () => {
  const h = pickerHarness({ value: workplace, name: "Company name differs from the saved Google branch name" });
  const tree = h.render(); button(tree, "Confirm workplace location").props.onClick();
  assert.equal(h.selections.length, 1);
  assert.strictEqual(h.selections[0], workplace);
  assert.equal(h.selections[0].placeId, "legacy-google-place");
  assert.equal(h.selections[0].name, workplace.name);
  h.render(); assert.equal(h.pending.at(-1), false);
});

test("address edits require fresh confirmation and invalid coordinates are rejected", () => {
  const h = pickerHarness({ value: workplace }); h.render(); h.props.address = "New branch address";
  let tree = h.render(); assert.equal(h.pending.at(-1), true);
  nodes(tree).find(node => node.type === "WorkplaceMap").props.onSelect({ latitude: NaN, longitude: 181 });
  assert.equal(h.selections.length, 0);
  h.props.address = ""; tree = h.render(); assert.equal(button(tree, "Confirm workplace location").props.disabled, true);
});

test("current location requests fresh GPS once, shows permission denial, and retry can be confirmed", () => {
  const oldNavigator = Object.getOwnPropertyDescriptor(globalThis, "navigator");
  const requests = [];
  Object.defineProperty(globalThis, "navigator", { configurable: true, value: { geolocation: { getCurrentPosition(success, failure, options) { requests.push({ success, failure, options }); } } } });
  try {
    const h = pickerHarness(); let tree = h.render(); assert.equal(requests.length, 0);
    button(tree, "Use my current location").props.onClick(); tree = h.render();
    assert.equal(button(tree, "Finding your location…").props.disabled, true); assert.equal(h.pending.at(-1), true);
    assert.deepEqual(requests[0].options, { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 });
    requests[0].failure({ code: 1 }); tree = h.render();
    assert.equal(h.pending.at(-1), false);
    assert.ok(nodes(tree).some(node => node.props?.role === "alert" && /permission was denied/.test(node.props.children)));
    button(tree, "Retry current location").props.onClick(); requests[1].success({ coords: { latitude: 21.13, longitude: 79.09 } });
    tree = h.render(); assert.deepEqual(nodes(tree).find(node => node.type === "WorkplaceMap").props.position, { latitude: 21.13, longitude: 79.09 });
    assert.equal(h.selections.at(-1), null);
    button(tree, "Confirm workplace location").props.onClick(); assert.equal(h.selections.at(-1).latitude, 21.13);
  } finally { if (oldNavigator) Object.defineProperty(globalThis, "navigator", oldNavigator); else delete globalThis.navigator; }
});

test("a late GPS response cannot replace a newer pin selection", () => {
  const oldNavigator = Object.getOwnPropertyDescriptor(globalThis, "navigator"); let success;
  Object.defineProperty(globalThis, "navigator", { configurable: true, value: { geolocation: { getCurrentPosition(resolve) { success = resolve; } } } });
  try {
    const h = pickerHarness(); let tree = h.render(); button(tree, "Use my current location").props.onClick();
    nodes(tree).find(node => node.type === "WorkplaceMap").props.onSelect({ latitude: 19, longitude: 72 });
    success({ coords: { latitude: 20, longitude: 73 } }); tree = h.render();
    assert.deepEqual(nodes(tree).find(node => node.type === "WorkplaceMap").props.position, { latitude: 19, longitude: 72 });
  } finally { if (oldNavigator) Object.defineProperty(globalThis, "navigator", oldNavigator); else delete globalThis.navigator; }
});

test("a browser throwing while requesting GPS clears loading and preserves the confirmed workplace", () => {
  const oldNavigator = Object.getOwnPropertyDescriptor(globalThis, "navigator");
  Object.defineProperty(globalThis, "navigator", { configurable: true, value: { geolocation: { getCurrentPosition() { throw new Error("Permissions policy blocked location"); } } } });
  try {
    const h = pickerHarness({ value: workplace }); let tree = h.render();
    button(tree, "Use my current location").props.onClick(); tree = h.render();
    assert.equal(button(tree, "Retry current location").props.disabled, false);
    assert.equal(h.pending.at(-1), false); assert.deepEqual(h.selections, []);
    assert.ok(nodes(tree).some(node => node.props?.role === "alert" && /could not request location/.test(node.props.children)));
  } finally { if (oldNavigator) Object.defineProperty(globalThis, "navigator", oldNavigator); else delete globalThis.navigator; }
});

test("Leaflet click and marker drag select wrapped coordinates; disabled map ignores edits and tiles retain attribution", () => {
  const selections = [], effects = []; let events;
  const map = { setView() {}, getZoom: () => 4 };
  const { default: Map } = load("components/OpenStreetMapWorkplaceMap.tsx", {
    react: { useEffect: effect => effects.push(effect) }, leaflet: { divIcon: options => options }, "leaflet/dist/leaflet.css": {},
    "react-leaflet": { MapContainer: "MapContainer", Marker: "Marker", TileLayer: "TileLayer", useMap: () => map, useMapEvents: handlers => { events = handlers; return map; } }, "@/lib/workplace-location": location,
  });
  function selection(disabled) {
    const tree = Map({ position: { latitude: 21, longitude: 79 }, disabled, onSelect: value => selections.push(value), onTileError() {} });
    const component = nodes(tree).find(node => typeof node.type === "function" && node.type.name === "Selection");
    return { tree, marker: component.type(component.props) };
  }
  const h = selection(false);
  events.click({ latlng: { wrap: () => ({ lat: 19, lng: 72 }) } });
  h.marker.props.eventHandlers.dragend({ target: { getLatLng: () => ({ wrap: () => ({ lat: 20, lng: 73 }) }) } });
  assert.deepEqual(selections, [{ latitude: 19, longitude: 72 }, { latitude: 20, longitude: 73 }]);
  const layer = nodes(h.tree).find(node => node.type === "TileLayer");
  assert.match(layer.props.attribution, /openstreetmap.org\/copyright/); assert.equal(layer.props.keepBuffer, 0);
  assert.equal(layer.props.referrerPolicy, "strict-origin-when-cross-origin");
  const readOnly = selection(true); events.click({ latlng: { wrap: () => ({ lat: 22, lng: 80 }) } });
  readOnly.marker.props.eventHandlers.dragend({ target: { getLatLng() { throw Error("must not read disabled marker"); } } });
  assert.equal(readOnly.marker.props.draggable, false); assert.equal(selections.length, 2);
});

test("coordinate validation accepts zero and geographic boundaries but rejects missing, infinite and out-of-range values", () => {
  for (const value of [{ latitude: 0, longitude: 0 }, { latitude: -90, longitude: 180 }]) assert.equal(location.validWorkplaceCoordinates(value), true);
  for (const value of [null, { latitude: Infinity, longitude: 1 }, { latitude: 91, longitude: 0 }, { latitude: 0, longitude: -181 }]) assert.equal(location.validWorkplaceCoordinates(value), false);
});
