const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs"), path = require("node:path"), ts = require("typescript");
const React = require("react"), { renderToStaticMarkup } = require("react-dom/server");
const flush = () => new Promise(resolve => setImmediate(resolve));
const endpoint = "/student/resume/analysis/corrections";
const values = { skills: ["Java"], certifications: ["Cloud fundamentals"], experience: [{ title: "Devloper", company: "Example Ltd", startDate: "2024", endDate: null }], education: [{ degree: "BE", field: "Computer Engineering", institution: "Example College" }] };
const original = { resumeId: 42, status: "PARSED", completedAt: "2026-10-30T07:00:00Z", ...values };
const response = { resumeId: 42, revision: 0, source: "AI_EXTRACTED", original, values, editedAt: null, editorId: null, editorName: null, history: [] };

function findAll(node, predicate) {
  if (Array.isArray(node)) return node.flatMap(child => findAll(child, predicate));
  if (!React.isValidElement(node)) return [];
  return [...(predicate(node) ? [node] : []), ...findAll(node.props.children, predicate)];
}
function harness(api, props = {}) {
  const states = [], effects = [], pending = []; let cursor = 0;
  const hooks = {
    useState(initial) { const i = cursor++; if (!(i in states)) states[i] = initial; return [states[i], value => { states[i] = typeof value === "function" ? value(states[i]) : value; }]; },
    useRef(initial) { const i = cursor++; if (!(i in states)) states[i] = { current: initial }; return states[i]; },
    useEffect(effect, deps) { const i = cursor++, previous = effects[i]; if (!previous || deps.some((value, index) => previous.deps[index] !== value)) { pending.push(() => { previous?.cleanup?.(); effects[i] = { deps, cleanup: effect() }; }); } },
  };
  const imports = { react: hooks, "@/lib/api": { api }, "@/lib/monthly-progress": { formatDateTime: value => value || "Not available" }, "@/lib/professional-profile": { resumeError: (cause, fallback) => cause?.response?.data?.message || fallback } };
  const m = { exports: {} };
  new Function("require", "module", "exports", ts.transpileModule(fs.readFileSync(path.join(__dirname, "../components/ResumeCorrectionEditor.tsx"), "utf8"), { compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText)(name => imports[name] || require(name), m, m.exports);
  const currentProps = { endpoint, resumeId: 42, ...props };
  function tree() { cursor = 0; const result = m.exports.ResumeCorrectionEditor(currentProps); while (pending.length) pending.shift()(); return result; }
  return {
    tree, html: () => renderToStaticMarkup(tree()),
    button: label => findAll(tree(), node => node.type === "button" && React.Children.toArray(node.props.children).join("") === label)[0],
    input: label => findAll(tree(), node => node.type === "input" && node.props["aria-label"] === label)[0],
    async mount() { tree(); await flush(); tree(); },
    async submit() { await findAll(tree(), node => node.type === "form")[0].props.onSubmit({ preventDefault() {} }); },
    async props(next) { Object.assign(currentProps, next); tree(); await flush(); tree(); },
    unmount() { effects.forEach(effect => effect?.cleanup?.()); },
  };
}

test("shows original extraction separately from IIIC corrections with editor and history", async () => {
  const corrected = { ...values, skills: ["Java", "PostgreSQL"] };
  const data = { ...response, revision: 1, values: corrected, source: "IIIC_REVIEWED", editedAt: "2026-10-30T08:00:00Z", editorName: "Coordinator", history: [{ revision: 1, values: corrected, source: "IIIC_REVIEWED", editedAt: "2026-10-30T08:00:00Z", editorName: "Coordinator" }] };
  const calls = [], h = harness({ async get(url) { calls.push(url); return { data }; } });
  await h.mount();
  assert.deepEqual(calls, [endpoint]);
  assert.match(h.html(), /IIIC-reviewed information/); assert.match(h.html(), /Coordinator/); assert.match(h.html(), /Original AI-extracted information/); assert.match(h.html(), /Correction history \(1\)/); assert.match(h.html(), /PostgreSQL/);
  h.unmount();
});

test("adds and removes repeatable entries then saves revision-bound corrections without changing original extraction", async () => {
  const updates = []; let saved = 0;
  const h = harness({ async get() { return { data: response }; }, async put(url, body) { updates.push({ url, body }); return { data: { ...response, ...body, revision: 1, source: "STUDENT_CORRECTED", editedAt: "2026-10-30T08:00:00Z", editorName: "Student" } }; } }, { onSaved() { saved++; } });
  await h.mount(); h.button("Edit parsed information").props.onClick();
  h.input("Experience 1 designation / role").props.onChange({ target: { value: "Developer" } });
  h.button("Add education").props.onClick(); h.input("Education 2 college / university").props.onChange({ target: { value: "Correct University" } });
  h.button("Remove experience 1").props.onClick();
  await h.submit();
  assert.equal(updates.length, 1); assert.equal(updates[0].url, endpoint); assert.equal(updates[0].body.resumeId, 42); assert.equal(updates[0].body.revision, 0);
  assert.deepEqual(updates[0].body.values.experience, []); assert.equal(updates[0].body.values.education[1].institution, "Correct University");
  assert.equal(original.experience[0].title, "Devloper"); assert.equal(saved, 1); assert.match(h.html(), /Resume corrections saved/); assert.match(h.html(), /Student-corrected information/); assert.equal(h.button("Save corrections"), undefined);
  h.unmount();
});

test("rejects blank entries and preserves edits when a concurrent save conflicts", async () => {
  let attempts = 0;
  const h = harness({ async get() { return { data: response }; }, async put() { attempts++; throw { response: { data: { message: "Information has changed. Reload before saving." } } }; } });
  await h.mount(); h.button("Edit parsed information").props.onClick(); h.button("Add skill").props.onClick(); await h.submit();
  assert.equal(attempts, 0); assert.match(h.html(), /Complete or remove empty skills/);
  findAll(h.tree(), node => node.type === "button" && node.props["aria-label"] === "Remove skills entry 2")[0].props.onClick();
  h.input("Experience 1 designation / role").props.onChange({ target: { value: "Corrected developer" } }); await h.submit();
  assert.equal(attempts, 1); assert.match(h.html(), /Information has changed/); assert.equal(h.input("Experience 1 designation / role").props.value, "Corrected developer");
  assert.ok(h.button("Reload saved information")); h.unmount();
});

test("ignores old resume responses after the selected resume changes", async () => {
  let resolve; const old = new Promise(done => { resolve = done; }); const signals = [];
  const h = harness({ get(url, options) { signals.push(options.signal); return signals.length === 1 ? old : Promise.resolve({ data: { ...response, resumeId: 43, values: { ...values, skills: ["Python"] } } }); } });
  await h.mount(); await h.props({ resumeId: 43 });
  assert.equal(signals[0].aborted, true); resolve({ data: response }); await flush();
  assert.match(h.html(), /Python/); h.unmount();
});

test("mentor read-only review preserves snapshots and history without edit or save controls", async () => {
  const h = harness({ async get() { return { data: response }; } }, { readOnly: true, endpoint: "/mentor/students/7/resume/analysis/corrections" });
  await h.mount();
  assert.match(h.html(), /Original AI-extracted information/); assert.match(h.html(), /Correction history/);
  assert.equal(h.button("Edit parsed information"), undefined); assert.equal(h.button("Save corrections"), undefined);
  assert.equal(findAll(h.tree(), node => node.type === "form").length, 0); h.unmount();
});
