const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs"), path = require("node:path"), ts = require("typescript");

function guardHarness() {
  const refs = []; let refCursor = 0; let pending = new Set();
  const react = {
    useRef(initial) { const i = refCursor++; if (!refs[i]) refs[i] = { current: initial }; return refs[i]; },
    useState(initial) { if (pending.size === 0 && typeof initial === "function") pending = initial(); return [pending, value => { pending = typeof value === "function" ? value(pending) : value; }]; },
    useCallback(fn) { return fn; },
  };
  const source = fs.readFileSync(path.join(__dirname, "../lib/action-guard.ts"), "utf8");
  const mod = { exports: {} };
  new Function("require", "module", "exports", ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 },
  }).outputText)(name => name === "react" ? react : require(name), mod, mod.exports);
  refCursor = 0;
  return mod.exports.useActionGuard();
}

test("same action and record emits one request while pending, then permits a retry", async () => {
  const guard = guardHarness(); let requests = 0, release;
  const pending = new Promise(resolve => { release = resolve; });
  const action = () => { requests++; return pending; };
  const first = guard.run("student:second-request:2", action);
  const duplicate = guard.run("student:second-request:2", action);
  assert.equal(requests, 1);
  assert.equal(await duplicate, undefined);
  release("saved"); await first;
  await guard.run("student:second-request:2", async () => { requests++; return "retried"; });
  assert.equal(requests, 2);
});

test("guards are scoped by action and internship record", async () => {
  const guard = guardHarness(); const calls = [];
  let releaseOne, releaseTwo;
  const one = guard.run("attendance:internship:11", () => { calls.push(11); return new Promise(resolve => { releaseOne = resolve; }); });
  const two = guard.run("attendance:internship:22", () => { calls.push(22); return new Promise(resolve => { releaseTwo = resolve; }); });
  assert.deepEqual(calls, [11, 22]);
  releaseOne(); releaseTwo(); await Promise.all([one, two]);
});
