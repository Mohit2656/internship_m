const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const ts = require("typescript");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");

function harness(api) {
  const states = [], effects = []; let cursor = 0;
  const hooks = {
    useState(initial) {
      const index = cursor++;
      if (!(index in states)) states[index] = initial;
      return [states[index], value => { states[index] = typeof value === "function" ? value(states[index]) : value; }];
    },
    useEffect(effect) { if (effects.length === 0) effects.push(effect); }
  };
  const module = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname, "../components/LateAttendancePermissions.tsx"), "utf8");
  new Function("require", "module", "exports", ts.transpileModule(source, {
    compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
  }).outputText)(name => name === "react" ? hooks : name === "@/lib/api" ? { api } : require(name), module, module.exports);
  function render() { cursor = 0; return module.exports.LateAttendancePermissions(); }
  function switches() {
    const result = [];
    function walk(node) { if (!node || typeof node !== "object") return; if (node.type === "input") result.push(node); React.Children.forEach(node.props?.children, walk); }
    walk(render()); return result;
  }
  return {
    html: () => renderToStaticMarkup(render()), switches,
    async mount() { render(); effects[0](); await new Promise(resolve => setImmediate(resolve)); },
    async toggle(index, enabled) { switches()[index].props.onChange({ target: { checked: enabled } }); await new Promise(resolve => setImmediate(resolve)); }
  };
}

const assigned = [
  { studentUserId: 11, studentName: "Assigned Student", uid: "ENR001", attendanceDate: "2026-10-22", attendanceZone: "Asia/Kolkata", enabled: false, authorizedBy: null, authorizedAt: null },
  { studentUserId: 12, studentName: "Other Assigned Student", uid: "ENR002", attendanceDate: "2026-10-22", attendanceZone: "Asia/Kolkata", enabled: false, authorizedBy: null, authorizedAt: null }
];

test("mentor permission toggle displays the server date and changes only the selected assigned student", async () => {
  const calls = [];
  const h = harness({
    async get(url) { calls.push({ url }); return { data: assigned }; },
    async put(url, body) { calls.push({ url, body }); return { data: { ...assigned[0], enabled: body.enabled, authorizedBy: 3 } }; }
  });
  await h.mount();
  assert.match(h.html(), /Late Attendance Permission/);
  assert.match(h.html(), /Assigned Student/); assert.match(h.html(), /ENR001/);
  assert.match(h.html(), /2026-10-22/); assert.match(h.html(), /Asia\/Kolkata/);
  assert.match(h.html(), /expires at the end of that day/);
  assert.equal(h.switches()[0].props.role, "switch");
  assert.equal(h.switches()[0].props.checked, false);
  await h.toggle(0, true);
  assert.deepEqual(calls[1], { url: "/mentor/attendance/late-permissions/11", body: { enabled: true } });
  assert.equal(h.switches()[0].props.checked, true); assert.equal(h.switches()[1].props.checked, false);
  assert.match(h.html(), /permission enabled for Assigned Student on 2026-10-22/);
  await h.toggle(0, false);
  assert.deepEqual(calls[2].body, { enabled: false });
  assert.equal(h.switches()[0].props.checked, false);
  assert.match(h.html(), /permission disabled for Assigned Student on 2026-10-22/);
});

test("a rejected permission change keeps the server status and displays the authorization error", async () => {
  const h = harness({
    async get() { return { data: assigned }; },
    async put() { throw { response: { data: { message: "Student is no longer assigned to you." } } }; }
  });
  await h.mount(); await h.toggle(0, true);
  assert.equal(h.switches()[0].props.checked, false);
  assert.match(h.html(), /role="alert"/); assert.match(h.html(), /Student is no longer assigned to you/);
});
