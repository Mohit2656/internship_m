const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const ts = require("typescript");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");
const source = fs.readFileSync(path.join(__dirname, "../components/WorkplacePlaceSelector.tsx"), "utf8");
const compiled = ts.transpileModule(source, { compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, esModuleInterop: true } }).outputText;
const component = { exports: {} };
new Function("require", "module", "exports", compiled)(name => name === "next/script" ? (() => null) : require(name), component, component.exports);
const render = value => renderToStaticMarkup(React.createElement(component.exports.WorkplacePlaceSelector, { value, onSelect() {} }));
test("missing API key is explicit and does not invent coordinates", () => {
  const original = process.env.NEXT_PUBLIC_GOOGLE_MAPS_API_KEY;
  delete process.env.NEXT_PUBLIC_GOOGLE_MAPS_API_KEY;
  try { const html = render(null); assert.match(html, /Google Places is not configured/); assert.doesNotMatch(html, /maps\/search/); }
  finally { if (original !== undefined) process.env.NEXT_PUBLIC_GOOGLE_MAPS_API_KEY = original; }
});
test("saved selected workplace shows address and an actual-coordinate Maps link", () => {
  const html = render({ placeId: "ChIJ-example", address: "Registered office", latitude: 30.733315, longitude: 76.779419 });
  assert.match(html, /Registered office/); assert.match(html, /query=30[.]733315%2C76[.]779419/);
  assert.match(html, /query_place_id=ChIJ-example/); assert.match(html, /after approval by your assigned mentor and III Cell/);
});
