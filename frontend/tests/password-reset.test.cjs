const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const ts = require("typescript");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");
function load(file, imports = {}) {
  const code = ts.transpileModule(fs.readFileSync(path.join(__dirname, file), "utf8"), { compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText;
  const module = { exports: {} };
  new Function("require", "module", "exports", code)(name => imports[name] ?? require(name), module, module.exports);
  return module.exports;
}
const helpers = load("../lib/password-reset.ts");
function harness(post) {
  const slots = []; let cursor = 0; let mounted = false; const effects = [];
  const hooks = { useState(initial) { const i = cursor++; if (!(i in slots)) slots[i] = initial; return [slots[i], value => { slots[i] = value; }]; }, useRef(initial) { const i = cursor++; if (!(i in slots)) slots[i] = { current: initial }; return slots[i]; }, useEffect(fn) { cursor++; if (!mounted) effects.push(fn); } };
  const { ResetPasswordButton } = load("../components/ResetPasswordButton.tsx", { react: hooks, "next/navigation": { usePathname: () => "/admin/students" }, "@/lib/api": { api: { post } }, "@/lib/password-reset": helpers });
  function render() { cursor = 0; const tree = ResetPasswordButton({ userId: 42, name: "Student Example", email: "student@example.edu" }); if (!mounted) { effects.forEach(fn => fn()); mounted = true; return render(); } return tree; }
  function nodes(tree, type) { const result = []; function walk(node) { if (!node || typeof node !== "object") return; if (node.type === type) result.push(node); React.Children.forEach(node.props?.children, walk); } walk(tree); return result; }
  function button(label) { return nodes(render(), "button").find(node => node.props.children === label); }
  return { render, button, html: () => renderToStaticMarkup(render()) };
}
const flush = () => new Promise(resolve => setImmediate(resolve));
test("confirmation precedes request; pending prevents duplicate submissions; close clears credential", async () => {
  let calls = 0, complete;
  const h = harness(() => { calls++; return new Promise(resolve => { complete = resolve; }); });
  h.button("Reset Password").props.onClick();
  assert.equal(calls, 0); assert.match(h.html(), /Student Example/);
  const confirm = h.button("Confirm reset"); confirm.props.onClick(); confirm.props.onClick();
  assert.equal(calls, 1); assert.equal(h.button("Resetting...").props.disabled, true); assert.equal(h.button("Cancel").props.disabled, true);
  complete({ data: { temporaryPassword: "generated-secret" } }); await flush();
  assert.match(h.html(), /generated-secret/); assert.match(h.html(), /Share this securely/);
  assert.equal(h.button("Confirm reset"), undefined);
  h.button("Close and clear").props.onClick(); assert.doesNotMatch(h.html(), /generated-secret/);
  h.button("Reset Password").props.onClick(); assert.doesNotMatch(h.html(), /generated-secret/);
});
test("cancel performs no reset and error permits retry without revealing server contents", async () => {
  let calls = 0;
  const h = harness(async () => { calls++; throw { response: { status: 403, data: { message: "secret-must-not-display" } } }; });
  h.button("Reset Password").props.onClick(); h.button("Cancel").props.onClick(); assert.equal(calls, 0);
  h.button("Reset Password").props.onClick(); h.button("Confirm reset").props.onClick(); await flush();
  assert.match(h.html(), /do not have permission/); assert.doesNotMatch(h.html(), /secret-must-not-display/); assert.equal(h.button("Confirm reset").props.disabled, false);
});
test("safe errors cover expired session, missing account, ineligible account and ambiguous network failure", () => {
  for (const status of [401,403,404,409,500]) assert.equal(typeof helpers.resetErrorMessage({ response: { status } }), "string");
  assert.match(helpers.resetErrorMessage(new Error("sensitive")), /server may have completed/);
});
