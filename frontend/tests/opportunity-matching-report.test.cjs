const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs"), path = require("node:path"), ts = require("typescript");
const React = require("react"), { renderToStaticMarkup } = require("react-dom/server");
const flush = () => new Promise(resolve => setImmediate(resolve));
const candidate = { studentId: 7, studentName: "First Student", uid: "UID7", branch: "COMPUTER_ENGINEERING", semester: 7, matchScore: 100, eligibility: "ELIGIBLE", eligibilityReasons: ["Configured requirements met"], matchedSkills: ["Java"], missingRequiredSkills: [], matchedPreferredSkills: ["Docker"], contextEvidence: ["Education: BE"], resumeAvailable: true, strongMatch: true };
const other = { ...candidate, studentId: 8, studentName: "Other Student", uid: "UID8", branch: "CIVIL", matchScore: 40, eligibility: "INELIGIBLE", matchedSkills: [], missingRequiredSkills: ["Java"], strongMatch: false };
const summary = { id: 11, triggerType: "PUBLICATION", algorithmVersion: "exact-skills-v1", createdAt: "2026-10-30T08:00:00Z" };
const run = { ...summary, opportunityId: 42, report: { title: "Software Intern", companyName: "Example Company", strongMatchThreshold: 75, scoringExplanation: "Required skills 80%, preferred skills 20%. A score is not a selection decision.", candidates: [candidate, other] } };

function load(file, imports = {}) {
  const m = { exports: {} };
  new Function("require", "module", "exports", ts.transpileModule(fs.readFileSync(path.join(__dirname, "..", file), "utf8"), { compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText)(name => imports[name] || require(name), m, m.exports);
  return m.exports;
}
const opportunities = load("lib/opportunities.ts");
function findAll(node, predicate) {
  if (Array.isArray(node)) return node.flatMap(child => findAll(child, predicate));
  if (!React.isValidElement(node)) return [];
  return [...(predicate(node) ? [node] : []), ...findAll(node.props.children, predicate)];
}
function harness(file, name, api, props) {
  const states = [], effects = [], pending = []; let cursor = 0;
  const hooks = {
    useState(initial) { const i = cursor++; if (!(i in states)) states[i] = initial; return [states[i], value => { states[i] = typeof value === "function" ? value(states[i]) : value; }]; },
    useEffect(effect, deps) { const i = cursor++, previous = effects[i]; if (!previous || deps.some((value, index) => previous.deps[index] !== value)) pending.push(() => { previous?.cleanup?.(); effects[i] = { deps, cleanup: effect() }; }); },
  };
  const downloads = [];
  const module = load(file, { react: hooks, "next/link": { default: ({ children, ...attributes }) => React.createElement("a", attributes, children) }, "@/lib/api": { api }, "@/lib/monthly-progress": { formatDateTime: value => value }, "@/lib/professional-profile": { async downloadResume(...args) { downloads.push(args); } }, "@/lib/opportunities": opportunities });
  function tree() { cursor = 0; const result = module[name](props); while (pending.length) pending.shift()(); return result; }
  return {
    tree, downloads, html: () => renderToStaticMarkup(tree()),
    button: label => findAll(tree(), n => n.type === "button" && React.Children.toArray(n.props.children).join("") === label)[0],
    field: label => findAll(tree(), n => n.props["aria-label"] === label)[0],
    async mount() { for (let i = 0; i < 4; i++) { tree(); await flush(); } },
    unmount() { effects.forEach(effect => effect?.cleanup?.()); },
  };
}

test("saved report presents scores, eligibility, profile links and authorized resume download", async () => {
  const calls = [];
  const h = harness("components/OpportunityMatchingReport.tsx", "OpportunityMatchingReport", { async get(url) { calls.push(url); return { data: url.endsWith("matching-runs") ? [summary] : url.endsWith("professional-profile") ? { resume: { originalFilename: "student.docx" } } : run }; } }, { opportunityId: "42" });
  await h.mount();
  assert.deepEqual(calls, ["/admin/opportunities/42/matching-runs", "/admin/opportunities/42/matching-runs/11"]);
  assert.match(h.html(), /100\/100/); assert.match(h.html(), /40\/100/); assert.match(h.html(), /INELIGIBLE/); assert.match(h.html(), /Education: BE/);
  assert.match(h.html(), /href="\/admin\/students\/7"/); assert.match(h.html(), /not a selection decision/);
  await h.button("Download resume").props.onClick();
  assert.deepEqual(h.downloads, [["/admin/students/7/resume/download", "student.docx"]]); h.unmount();
});

test("matching table filters candidates and reverses score order without new API requests", async () => {
  let requests = 0;
  const h = harness("components/OpportunityMatchingReport.tsx", "OpportunityMatchingReport", { async get(url) { requests++; return { data: url.endsWith("matching-runs") ? [summary] : run }; } }, { opportunityId: "42" });
  await h.mount();
  h.field("Matching eligibility").props.onChange({ target: { value: "ELIGIBLE" } });
  assert.match(h.html(), /First Student/); assert.doesNotMatch(h.html(), /Other Student/);
  h.field("Matching eligibility").props.onChange({ target: { value: "ALL" } });
  h.field("Score order").props.onChange({ target: { value: "ASC" } });
  assert.ok(h.html().indexOf("Other Student") < h.html().indexOf("First Student"));
  h.field("Search matching candidates").props.onChange({ target: { value: "UID8" } });
  assert.doesNotMatch(h.html(), /First Student/); assert.match(h.html(), /Other Student/);
  assert.equal(requests, 2); h.unmount();
});

test("manual rerun posts to the current opportunity and appends immutable history", async () => {
  const posted = [], calls = []; const next = { ...run, id: 12, triggerType: "MANUAL" };
  const h = harness("components/OpportunityMatchingReport.tsx", "OpportunityMatchingReport", { async get(url) { calls.push(url); return { data: url.endsWith("matching-runs") ? [summary] : url.endsWith("/12") ? next : run }; }, async post(url) { posted.push(url); return { data: next }; } }, { opportunityId: "42" });
  await h.mount(); await h.button("Recalculate matches").props.onClick(); await h.mount();
  assert.deepEqual(posted, ["/admin/opportunities/42/matching-runs"]); assert.match(h.html(), /Run 12/); assert.match(h.html(), /Run 11/);
  h.field("Matching history").props.onChange({ target: { value: "11" } }); await h.mount();
  assert.equal(calls.at(-1), "/admin/opportunities/42/matching-runs/11"); h.unmount();
});

test("failed history selection clears the previous report instead of presenting mismatched snapshot data", async () => {
  const h = harness("components/OpportunityMatchingReport.tsx", "OpportunityMatchingReport", { async get(url) { if (url.endsWith("/10")) throw Error("offline"); return { data: url.endsWith("matching-runs") ? [summary, { ...summary, id: 10 }] : run }; } }, { opportunityId: "42" });
  await h.mount(); h.field("Matching history").props.onChange({ target: { value: "10" } }); await h.mount();
  assert.match(h.html(), /selected matching report could not be loaded/); assert.doesNotMatch(h.html(), /First Student/); h.unmount();
});

test("student sees their current deterministic score and clear limits", async () => {
  const calls = [];
  const h = harness("components/OpportunityScorePanel.tsx", "OpportunityScorePanel", { async get(url) { calls.push(url); return { data: candidate }; } }, { opportunityId: 42 });
  await h.mount(); assert.deepEqual(calls, ["/student/opportunities/42/matching-score"]);
  assert.match(h.html(), /100\/100/); assert.match(h.html(), /Strong match/); assert.match(h.html(), /Java, Docker/); assert.match(h.html(), /does not imply company selection/); assert.match(h.html(), /review other listed requirements manually/); h.unmount();
});

test("CSV preserves quoting and neutralizes spreadsheet formulas", () => {
  const csv = opportunities.matchingCsv([{ ...candidate, studentName: '=HYPERLINK("bad")', uid: "UID,7" }]);
  assert.match(csv, /"'=HYPERLINK\(""bad""\)"/); assert.match(csv, /"UID,7"/); assert.match(csv, /"100"/);
  for (const prefix of ["=", "+", "-", "@", "\t", "\r", "\n", "\u0001="]) {
    assert.ok(opportunities.matchingCsv([{ ...candidate, studentName: prefix + "test" }]).includes(`"'${prefix}test"`));
  }
});
