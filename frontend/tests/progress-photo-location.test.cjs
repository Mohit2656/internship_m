const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const ts = require("typescript");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");
const source = fs.readFileSync(path.join(__dirname, "../components/ProgressPhotoLocation.tsx"), "utf8");
const compiled = ts.transpileModule(source, { compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText;
const component = { exports: {} };
const mapSource=fs.readFileSync(path.join(__dirname,"../components/LocationMapLink.tsx"),"utf8");
const mapModule={exports:{}};
new Function("require","module","exports",ts.transpileModule(mapSource,{compilerOptions:{jsx:ts.JsxEmit.ReactJSX,module:ts.ModuleKind.CommonJS}}).outputText)(require,mapModule,mapModule.exports);
new Function("require", "module", "exports", compiled)(name => name === "@/components/LocationMapLink" ? mapModule.exports : require(name), component, component.exports);
const render = report => renderToStaticMarkup(React.createElement(component.exports.ProgressPhotoLocation, { report }));
test("valid API coordinates render with an encoded Google Maps link", () => {
  const html = render({ exifLatitude: 21.125, exifLongitude: 79.0875, geoVerificationStatus: "VERIFIED" });
  assert.match(html, /Latitude/); assert.match(html, /21[.]125/); assert.match(html, /79[.]0875/);
  assert.match(html, /query=21[.]125%2C79[.]0875/); assert.match(html, /noopener noreferrer/);
});
test("missing coordinates show an explicit unavailable state without a Maps link", () => {
  const html = render({ exifLatitude: null, exifLongitude: null, geoVerificationStatus: "MISSING_GPS" });
  assert.match(html, /no GPS coordinates were found/); assert.doesNotMatch(html, /google[.]com/);
});
test("invalid and partial GPS cannot generate a Maps link", () => {
  for (const report of [{ exifLatitude: 91, exifLongitude: 79, geoVerificationStatus: "INVALID_GPS" }, { exifLatitude: 21, exifLongitude: null, geoVerificationStatus: null }]) {
    assert.match(render(report), /location unavailable/); assert.doesNotMatch(render(report), /google[.]com/);
  }
});
test("zero coordinates are valid and legacy coordinates retain unassessed status", () => {
  assert.match(render({ exifLatitude: 0, exifLongitude: 0, geoVerificationStatus: "VERIFIED" }), /query=0%2C0/);
  assert.match(render({ exifLatitude: 21, exifLongitude: 79, geoVerificationStatus: null }), /query=21%2C79/);
});

test("workplace snapshot and distance are displayed independently of missing photo GPS",()=>{
 const html=render({exifLatitude:null,exifLongitude:null,geoVerificationStatus:"MISSING_GPS",workplaceLatitude:21.125,workplaceLongitude:79.0875,workplaceAddress:"Registered branch",workplaceVerificationStatus:"PHOTO_MISSING_GPS",workplaceVerificationReason:"Missing photo GPS",workplaceDistanceMeters:null});
 assert.match(html,/Registered branch/);assert.match(html,/PHOTO MISSING GPS/);assert.match(html,/View report workplace/);assert.doesNotMatch(html,/View photo coordinates/);
});
