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

const workplace = load("lib/workplace-location.ts");
const links = load("lib/google-maps-location-link.ts", { "@/lib/workplace-location": workplace });
const nodes = tree => !tree || typeof tree !== "object" ? [] : [tree, ...[tree.props?.children].flat(Infinity).flatMap(nodes)];
const text = tree => tree == null || typeof tree === "boolean" ? "" : typeof tree !== "object" ? String(tree) : [tree.props?.children].flat(Infinity).map(text).join("");
const button = (tree, label) => nodes(tree).find(node => node.type === "button" && text(node) === label);

test("explicit coordinate search URLs are recognized but automatic persistence remains blocked", () => {
  const result = links.inspectGoogleMapsLink("https://www.google.com/maps/search/?api=1&query=21.1458%2C79.0882");
  assert.equal(result.kind, "EXPLICIT_COORDINATES");
  assert.deepEqual(result.referencedCoordinates, { latitude: 21.1458, longitude: 79.0882 });
  assert.equal(result.automaticPersistenceAllowed, false);
});

test("place metadata and viewport centres are distinguished and never treated as verified", () => {
  const place = links.inspectGoogleMapsLink("https://www.google.com/maps/place/Office/data=!3d21.1458!4d79.0882");
  assert.equal(place.kind, "PLACE_COORDINATE_METADATA");
  assert.equal(place.automaticPersistenceAllowed, false);
  const viewport = links.inspectGoogleMapsLink("https://www.google.com/maps/place/Office/@21.1458,79.0882,17z");
  assert.equal(viewport.kind, "VIEWPORT_CENTRE");
  assert.match(viewport.message, /not proof/i);
});

test("text searches and place-ID-only URLs require manual coordinates", () => {
  assert.equal(links.inspectGoogleMapsLink("https://www.google.com/maps/search/?api=1&query=Virtual+Galaxy+Nagpur").kind, "TEXT_SEARCH");
  assert.equal(links.inspectGoogleMapsLink("https://www.google.com/maps/search/?api=1&query=Office&query_place_id=ChIJExample").kind, "PLACE_ID_ONLY");
});

test("directions, shortened, invalid and non-Google links are rejected without fetching", () => {
  assert.equal(links.inspectGoogleMapsLink("https://www.google.com/maps/dir/21.1,79.1/21.2,79.2").kind, "DIRECTIONS");
  assert.equal(links.inspectGoogleMapsLink("https://maps.app.goo.gl/example").kind, "SHORT_LINK");
  assert.equal(links.inspectGoogleMapsLink("not a URL").kind, "INVALID");
  assert.equal(links.inspectGoogleMapsLink("https://evil.example/maps/@21,79,17z").kind, "INVALID");
  assert.equal(links.inspectGoogleMapsLink("http://www.google.com/maps/@21,79,17z").kind, "INVALID");
});

test("manual coordinate validation requires both finite in-range values and identifies reversal", () => {
  assert.deepEqual(links.parseManualCoordinates("21.1458", "79.0882"), { ok: true, coordinates: { latitude: 21.1458, longitude: 79.0882 } });
  assert.equal(links.parseManualCoordinates("", "79").latitudeError, "Latitude is required.");
  assert.match(links.parseManualCoordinates("NaN", "79").latitudeError, /finite/);
  assert.match(links.parseManualCoordinates("120", "21").formError, /reversed/);
  assert.match(links.parseManualCoordinates("91", "181").latitudeError, /-90 and 90/);
  assert.match(links.parseManualCoordinates("91", "181").longitudeError, /-180 and 180/);
});

function componentHarness() {
  const states = [], previews = [], edits = [];
  let cursor = 0;
  const hooks = { useState(initial) { const i = cursor++; if (!(i in states)) states[i] = typeof initial === "function" ? initial() : initial; return [states[i], value => { states[i] = typeof value === "function" ? value(states[i]) : value; }]; } };
  const { GoogleMapsLocationLinkInput } = load("components/GoogleMapsLocationLinkInput.tsx", {
    react: hooks,
    "@/lib/google-maps-location-link": links,
  });
  const props = { onManualCoordinates: value => previews.push(value), onManualEdit: () => edits.push(true) };
  return { previews, edits, render() { cursor = 0; return GoogleMapsLocationLinkInput(props); } };
}

test("checking any link reveals manual fallback but cannot create a coordinate draft", () => {
  const h = componentHarness(); let tree = h.render();
  const url = nodes(tree).find(node => node.type === "input" && node.props.type === "url");
  url.props.onChange({ target: { value: "https://www.google.com/maps/search/?api=1&query=21.1458%2C79.0882" } });
  tree = h.render(); button(tree, "Check link").props.onClick(); tree = h.render();
  assert.ok(nodes(tree).some(node => node.props?.["data-link-kind"] === "EXPLICIT_COORDINATES"));
  assert.match(text(tree), /Automatic use is disabled/);
  assert.equal(nodes(tree).filter(node => node.props?.inputMode === "decimal").length, 2);
  assert.deepEqual(h.previews, []);
});

test("incorrect-location fallback validates, previews manual coordinates and reports edits", () => {
  const h = componentHarness(); let tree = h.render();
  button(tree, "Enter coordinates manually").props.onClick(); tree = h.render();
  let fields = nodes(tree).filter(node => node.props?.inputMode === "decimal");
  fields[0].props.onChange({ target: { value: "120" } });
  fields[1].props.onChange({ target: { value: "21" } });
  tree = h.render(); button(tree, "Preview manual coordinates").props.onClick(); tree = h.render();
  assert.match(text(tree), /appear to be reversed/);
  fields = nodes(tree).filter(node => node.props?.inputMode === "decimal");
  fields[0].props.onChange({ target: { value: "21.1458" } });
  fields[1].props.onChange({ target: { value: "79.0882" } });
  tree = h.render(); button(tree, "Preview manual coordinates").props.onClick();
  assert.deepEqual(h.previews, [{ latitude: 21.1458, longitude: 79.0882 }]);
  assert.equal(h.edits.length, 4);
});
