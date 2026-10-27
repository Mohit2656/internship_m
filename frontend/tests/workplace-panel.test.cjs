const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const ts = require("typescript");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");

function load(file, imports = {}) {
  const module = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname, "../components/" + file + ".tsx"), "utf8");
  new Function("require", "module", "exports", ts.transpileModule(source, {
    compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText)(name => Object.hasOwn(imports, name) ? imports[name] : require(name), module, module.exports);
  return module.exports;
}

const maps = load("LocationMapLink");
const picker = () => React.createElement("div", { "data-workplace-picker": "openstreetmap" }, "OpenStreetMap workplace picker");
const location = {
  id: 1, latitude: 21.125, longitude: 79.0875, geofenceRadiusMeters: 200, revision: 4, status: "APPROVED",
  workplaceName: "Actual registered branch", workplaceAddress: "Registered branch address", placeId: null,
  companyName: "Company", confirmedAt: null, mentorDecision: "APPROVE", iiicDecision: "APPROVE",
};

function panel(scope, value) {
  let cursor = 0;
  const seed = { 3: value, 4: value ? String(value.latitude) : "", 5: value ? String(value.longitude) : "", 6: "200", 7: false };
  const hooks = { useState(initial) { const i = cursor++; return [i in seed ? seed[i] : initial, () => {}]; }, useRef(initial) { return { current: initial }; }, useEffect() {} };
  const { InternshipLocationManager } = load("InternshipLocationManager", {
    react: hooks, "@/lib/api": { api: {} },
    "@/components/OpenStreetMapWorkplacePicker": { OpenStreetMapWorkplacePicker: picker },
    "@/components/LocationMapLink": maps,
  });
  return renderToStaticMarkup(InternshipLocationManager({ studentId: 1, scope }));
}

function elements(tree) {
  return !tree || typeof tree !== "object" ? [] : [tree, ...[tree.props?.children].flat(Infinity).flatMap(elements)];
}
const text = tree => !tree || typeof tree === "boolean" ? "" : typeof tree !== "object" ? String(tree) : [tree.props?.children].flat(Infinity).map(text).join("");
const button = (tree, label) => {
  const result = elements(tree).find(element => element.type === "button" && text(element) === label);
  assert.ok(result, `Button '${label}' should be rendered`);
  return result;
};
const radiusInput = tree => elements(tree).find(element => element.type === "input" && element.props.max === "5000");

// Exercise hooks, handlers, and effect cleanup without a browser or live API.
function harness(initialProps = {}) {
  const states = [], refs = [], dependencies = [], cleanups = [], effects = [], requests = [];
  let stateIndex = 0, refIndex = 0, effectIndex = 0;
  const props = { studentId: 1, scope: "admin", ...initialProps };
  const hooks = {
    useState(initial) {
      const i = stateIndex++;
      if (!(i in states)) states[i] = typeof initial === "function" ? initial() : initial;
      return [states[i], next => { states[i] = typeof next === "function" ? next(states[i]) : next; }];
    },
    useRef(initial) { const i = refIndex++; return refs[i] ??= { current: initial }; },
    useEffect(effect, deps) {
      const i = effectIndex++;
      if (!dependencies[i] || !deps || deps.some((value, j) => !Object.is(value, dependencies[i][j]))) {
        dependencies[i] = deps;
        effects.push(() => { cleanups[i]?.(); cleanups[i] = effect(); });
      }
    },
  };
  const api = Object.fromEntries(["get", "post", "put"].map(method => [method, (url, body, options) => {
    let resolve, reject;
    const response = new Promise((success, failure) => { resolve = success; reject = failure; });
    requests.push({ method, url, ...(method === "get" ? { options: body } : { body, options }),
      resolve: data => resolve({ data }), reject });
    return response;
  }]));
  const { InternshipLocationManager } = load("InternshipLocationManager", {
    react: hooks, "@/lib/api": { api },
    "@/components/OpenStreetMapWorkplacePicker": { OpenStreetMapWorkplacePicker: picker },
    "@/components/LocationMapLink": maps,
  });
  const result = {
    requests,
    render(nextProps = {}) {
      Object.assign(props, nextProps);
      stateIndex = 0; refIndex = 0; effectIndex = 0;
      const tree = InternshipLocationManager(props);
      effects.splice(0).forEach(effect => effect());
      return tree;
    },
    async settle() {
      for (let i = 0; i < 8; i++) await Promise.resolve();
      return result.render();
    },
    async loaded(value = location) {
      result.render();
      requests.at(-1).resolve(value);
      return result.settle();
    },
    click(tree, label) {
      const target = button(tree, label);
      if (target.props.disabled) return false;
      target.props.onClick();
      return true;
    },
    picker(tree) {
      const component = elements(tree).find(element => element.type === picker);
      assert.ok(component, "OpenStreetMap correction picker should be rendered");
      return component.props;
    },
    close() { cleanups.forEach(cleanup => cleanup?.()); },
  };
  return result;
}

function browserEvents(t) {
  const originalWindow = Object.getOwnPropertyDescriptor(globalThis, "window");
  const originalDocument = Object.getOwnPropertyDescriptor(globalThis, "document");
  const windowListeners = new Map(), documentListeners = new Map(), intervals = new Map(), cleanups = [];
  let timerId = 0;
  const surface = listeners => ({
    addEventListener(name, handler) { if (!listeners.has(name)) listeners.set(name, new Set()); listeners.get(name).add(handler); },
    removeEventListener(name, handler) { listeners.get(name)?.delete(handler); },
  });
  const doc = { ...surface(documentListeners), visibilityState: "visible" };
  Object.defineProperty(globalThis, "window", { configurable: true, value: {
    ...surface(windowListeners),
    setInterval(handler, delay) { const id = ++timerId; intervals.set(id, { handler, delay }); return id; },
    clearInterval(id) { intervals.delete(id); },
  } });
  Object.defineProperty(globalThis, "document", { configurable: true, value: doc });
  t.after(() => {
    cleanups.forEach(cleanup => cleanup());
    if (originalWindow) Object.defineProperty(globalThis, "window", originalWindow); else delete globalThis.window;
    if (originalDocument) Object.defineProperty(globalThis, "document", originalDocument); else delete globalThis.document;
  });
  return {
    focus() { [...windowListeners.get("focus") ?? []].forEach(handler => handler()); },
    visibility(value) { doc.visibilityState = value; [...documentListeners.get("visibilitychange") ?? []].forEach(handler => handler()); },
    poll() { [...intervals.values()].forEach(({ handler, delay }) => { assert.equal(delay, 30_000); handler(); }); },
    listeners() { return [...windowListeners.values(), ...documentListeners.values()].reduce((sum, listeners) => sum + listeners.size, 0); },
    cleanup(callback) { cleanups.push(callback); },
    get timers() { return intervals.size; },
  };
}

test("registered branch displays actual address, read-only coordinates and Maps link without a Google Places dependency", () => {
  const html = panel("admin", location);
  assert.match(html, /Actual registered branch/);
  assert.match(html, /Registered branch address/);
  assert.match(html, /query=21[.]125%2C79[.]0875/);
  assert.equal((html.match(/readOnly=""/g) || []).length, 2);
  assert.match(html, /Correct registered workplace/);
  assert.doesNotMatch(html, /Google Places is not configured|OpenStreetMap workplace picker/);
});

test("mentor panel is read-only and has no correction or confirmation actions", () => {
  const html = panel("mentor", location);
  assert.equal((html.match(/readOnly=""/g) || []).length, 3);
  assert.doesNotMatch(html, /Correct registered workplace|Save radius|Confirm location/);
  assert.match(html, /data-button-variant="approved"[^>]*disabled=""[^>]*>Approved<|disabled=""[^>]*data-button-variant="approved"[^>]*>Approved</);
  assert.doesNotMatch(html, /Approve workplace/);
  assert.match(html, /Reject workplace/);
});

test("missing and legacy workplaces remain explicit without inventing a branch", () => {
  const missing = panel("admin", null);
  assert.match(missing, /No workplace coordinates are registered/);
  assert.match(missing, />Not registered</);
  assert.doesNotMatch(missing, /maps\/search/);
  assert.match(panel("admin", { ...location, workplaceAddress: null, workplaceName: null }), /Legacy coordinates/);
});

test("pending approval identifies the missing reviewer and never invents a final approval", () => {
  const pending = { ...location, status: "PENDING" };
  const mentorMissing = panel("admin", { ...pending, mentorDecision: null });
  assert.match(mentorMissing, />Awaiting mentor</);
  assert.match(mentorMissing, /III Cell approval is recorded[.] Waiting for the assigned mentor to approve this workplace[.]/);
  const iiicMissing = panel("mentor", { ...pending, iiicDecision: null });
  assert.match(iiicMissing, />Awaiting III Cell</);
  assert.match(iiicMissing, /Mentor approval is recorded[.] Waiting for III Cell to approve this workplace[.]/);
  const bothMissing = panel("student", { ...pending, mentorDecision: null, iiicDecision: null });
  assert.match(bothMissing, />Awaiting mentor and III Cell</);
  assert.match(bothMissing, /Waiting for the assigned mentor and III Cell to approve this workplace[.]/);
  const inconsistent = panel("admin", pending);
  assert.match(inconsistent, />Approval needs review</);
  for (const html of [mentorMissing, iiicMissing, bothMissing, inconsistent]) assert.doesNotMatch(html, />Approved</);
  assert.match(panel("student", location), />Approved</);
  assert.match(panel("student", { ...location, status: "REJECTED" }), />Rejected</);
});

test("students can read independent decisions and rejection reasons but cannot review or correct", () => {
  const html = panel("student", { ...location, revision: 2, status: "REJECTED", mentorDecision: "REJECT", mentorRemarks: "Wrong branch selected", iiicDecision: "APPROVE" });
  assert.match(html, /Wrong branch selected/);
  assert.match(html, /Mentor: REJECT/);
  assert.match(html, /III Cell: APPROVE/);
  assert.doesNotMatch(html, /Approve workplace|Reject workplace|Save radius|Correct registered workplace/);
  assert.equal((html.match(/readOnly=""/g) || []).length, 3);
});

test("III Cell review posts the current revision, displays the returned pending decision, and refreshes the final status", async () => {
  const h = harness();
  let tree = await h.loaded({ ...location, status: "PENDING", mentorDecision: null, iiicDecision: null });
  assert.equal(h.requests[0].url, "/admin/students/1/internship/location");
  assert.equal(h.click(tree, "Approve workplace"), true);
  const review = h.requests.at(-1);
  assert.equal(review.method, "post");
  assert.equal(review.url, "/admin/students/1/internship/location/review");
  assert.deepEqual(review.body, { revision: 4, decision: "APPROVE", remarks: null });
  review.resolve({ ...location, status: "PENDING", mentorDecision: null, iiicDecision: "APPROVE", confirmedAt: "2026-10-27T09:00:00Z" });
  tree = await h.settle();
  assert.match(renderToStaticMarkup(tree), />Awaiting mentor</);
  assert.match(renderToStaticMarkup(tree), /III Cell: APPROVE/);
  assert.doesNotMatch(renderToStaticMarkup(tree), />Approved</);
  assert.equal(h.click(tree, "Refresh status"), true);
  h.render();
  const refresh = h.requests.at(-1);
  assert.equal(refresh.method, "get");
  assert.equal(refresh.url, "/admin/students/1/internship/location");
  refresh.resolve({ ...location, confirmedAt: "2026-10-27T09:00:00Z", mentorDecision: "APPROVE" });
  tree = await h.settle();
  assert.match(renderToStaticMarkup(tree), />Approved</);
  assert.match(renderToStaticMarkup(tree), /Mentor: APPROVE/);
});

test("unsaved radius blocks review and refresh until the server saves its new revision", async () => {
  const h = harness();
  let tree = await h.loaded();
  radiusInput(tree).props.onChange({ target: { value: "250" } });
  tree = h.render();
  assert.equal(button(tree, "Approved").props.disabled, true);
  assert.equal(button(tree, "Approved").props["data-button-variant"], "approved");
  assert.equal(button(tree, "Reject workplace").props.disabled, true);
  assert.equal(button(tree, "Refresh status").props.disabled, true);
  assert.match(renderToStaticMarkup(tree), /Saving a changed radius clears both approvals and requires a new review[.]/);
  assert.equal(h.click(tree, "Save radius"), true);
  const save = h.requests.at(-1);
  assert.equal(save.method, "put");
  assert.equal(save.url, "/admin/students/1/internship/location/radius");
  assert.deepEqual(save.body, { radiusMeters: 250 });
  save.resolve({ ...location, geofenceRadiusMeters: 250, revision: 5, status: "PENDING", mentorDecision: null, iiicDecision: null });
  tree = await h.settle();
  assert.match(renderToStaticMarkup(tree), />Awaiting mentor and III Cell</);
  assert.equal(button(tree, "Approve workplace").props.disabled, false);
  assert.notEqual(button(tree, "Approve workplace").props["data-button-variant"], "approved");
  assert.equal(button(tree, "Refresh status").props.disabled, false);
  assert.equal(h.click(tree, "Approve workplace"), true);
  assert.equal(h.requests.at(-1).body.revision, 5);
});

test("audited correction uses the OSM picker and refuses an unconfirmed or moved pin", async () => {
  const h = harness();
  let tree = await h.loaded();
  h.click(tree, "Correct registered workplace");
  tree = h.render();
  const initialPicker = h.picker(tree);
  assert.equal(initialPicker.value.address, location.workplaceAddress);
  assert.equal(initialPicker.value.latitude, location.latitude);
  assert.equal(initialPicker.disabled, false);
  assert.equal(typeof initialPicker.onPendingChange, "function");
  assert.doesNotMatch(renderToStaticMarkup(tree), /Google Places is not configured|Maps API key/);
  const reason = elements(tree).find(element => element.type === "textarea");
  reason.props.onChange({ target: { value: "Correct branch entrance" } });
  const chosen = { name: "Corrected branch", placeId: null, address: "Correct entrance address", latitude: 19.076, longitude: 72.8777 };
  initialPicker.onSelect(chosen);
  initialPicker.onPendingChange(true);
  tree = h.render();
  assert.equal(button(tree, "Save audited correction").props.disabled, true);
  h.picker(tree).onSelect(null);
  h.picker(tree).onPendingChange(false);
  tree = h.render();
  assert.equal(button(tree, "Save audited correction").props.disabled, true);
  assert.equal(h.requests.length, 1);
  h.picker(tree).onSelect(chosen);
  tree = h.render();
  assert.equal(h.click(tree, "Save audited correction"), true);
  const save = h.requests.at(-1);
  assert.equal(save.url, "/admin/students/1/internship/location/correction");
  assert.deepEqual(save.body, { workplace: chosen, radiusMeters: 200, reason: "Correct branch entrance" });
  save.resolve({ ...location, workplaceName: chosen.name, workplaceAddress: chosen.address, latitude: chosen.latitude,
    longitude: chosen.longitude, revision: 5, status: "PENDING", mentorDecision: null, iiicDecision: null });
  tree = await h.settle();
  const html = renderToStaticMarkup(tree);
  assert.match(html, /Corrected branch|Correct entrance address/);
  assert.match(html, />Awaiting mentor and III Cell</);
  assert.doesNotMatch(html, /OpenStreetMap workplace picker/);
});

test("an aborted older request cannot replace the newly selected student's workplace", async () => {
  const h = harness();
  h.render();
  const oldRequest = h.requests[0];
  h.render({ studentId: 2 });
  const currentRequest = h.requests.at(-1);
  assert.equal(oldRequest.options.signal.aborted, true);
  assert.equal(currentRequest.url, "/admin/students/2/internship/location");
  currentRequest.resolve({ ...location, workplaceName: "Current student's branch" });
  await h.settle();
  oldRequest.resolve({ ...location, workplaceName: "Stale student's branch" });
  const tree = await h.settle();
  const html = renderToStaticMarkup(tree);
  assert.match(html, /Current student&#x27;s branch/);
  assert.doesNotMatch(html, /Stale student/);
});

test("student panel refetches each persisted approval stage on focus and visibility without fabricating approval", async t => {
  const browser = browserEvents(t);
  const h = harness({ scope: "student", studentId: undefined });
  browser.cleanup(() => h.close());
  const pending = { ...location, status: "PENDING", mentorDecision: null, iiicDecision: null };
  let tree = await h.loaded(pending);
  assert.equal(h.requests[0].url, "/student/internship/location");
  assert.match(renderToStaticMarkup(tree), />Awaiting mentor and III Cell</);
  browser.focus();
  assert.equal(h.requests.length, 2);
  assert.equal(h.requests.at(-1).url, "/student/internship/location");
  assert.match(renderToStaticMarkup(h.render()), />Awaiting mentor and III Cell</);
  h.requests.at(-1).resolve({ ...pending, mentorDecision: "APPROVE", mentorReviewerId: 55 });
  tree = await h.settle();
  assert.match(renderToStaticMarkup(tree), />Awaiting III Cell</);
  assert.doesNotMatch(renderToStaticMarkup(tree), />Approved</);
  const requestCount = h.requests.length;
  browser.visibility("hidden");
  assert.equal(h.requests.length, requestCount);
  browser.visibility("visible");
  assert.equal(h.requests.length, requestCount + 1);
  h.requests.at(-1).resolve(location);
  tree = await h.settle();
  const html = renderToStaticMarkup(tree);
  assert.match(html, />Approved</);
  assert.match(html, /Both the assigned mentor and III Cell have approved this workplace/);
  assert.match(html, /Registered branch address/);
  assert.match(html, /query=21[.]125%2C79[.]0875/);
  assert.doesNotMatch(html, /Approve workplace|Reject workplace|Correct registered workplace/);
});

test("student panel polls the authoritative record and a reopened view retains approved state", async t => {
  const browser = browserEvents(t);
  const h = harness({ scope: "student", studentId: undefined });
  await h.loaded({ ...location, status: "PENDING", iiicDecision: null });
  assert.equal(browser.timers, 1);
  browser.poll();
  assert.equal(h.requests.at(-1).url, "/student/internship/location");
  h.requests.at(-1).resolve(location);
  assert.match(renderToStaticMarkup(await h.settle()), />Approved</);
  h.close();
  assert.equal(browser.timers, 0);
  assert.equal(browser.listeners(), 0);
  const reopened = harness({ scope: "student", studentId: undefined });
  browser.cleanup(() => reopened.close());
  const tree = await reopened.loaded(location);
  assert.match(renderToStaticMarkup(tree), />Approved</);
  assert.equal(reopened.requests.length, 1);
});

test("student background refresh displays server rejection and preserves the saved branch", async t => {
  const browser = browserEvents(t);
  const h = harness({ scope: "student", studentId: undefined });
  browser.cleanup(() => h.close());
  await h.loaded({ ...location, status: "PENDING", mentorDecision: null });
  browser.focus();
  assert.equal(h.requests.length, 2);
  h.requests.at(-1).resolve({ ...location, status: "REJECTED", mentorDecision: "REJECT", mentorRemarks: "Wrong company branch selected" });
  const html = renderToStaticMarkup(await h.settle());
  assert.match(html, />Rejected</);
  assert.match(html, /Wrong company branch selected/);
  assert.match(html, /Registered branch address/);
  assert.doesNotMatch(html, />Approved</);
});

test("a failed review or status refresh retains the last authoritative state", async t => {
  const browser = browserEvents(t);
  const h = harness();
  browser.cleanup(() => h.close());
  let tree = await h.loaded({ ...location, status: "PENDING", mentorDecision: null, iiicDecision: null });
  h.click(tree, "Approve workplace");
  h.requests.at(-1).reject({ response: { data: { message: "Workplace review could not be saved" } } });
  tree = await h.settle();
  assert.match(renderToStaticMarkup(tree), /Workplace review could not be saved/);
  assert.match(renderToStaticMarkup(tree), />Awaiting mentor and III Cell</);
  assert.equal(button(tree, "Approve workplace").props.disabled, false);
  assert.notEqual(button(tree, "Approve workplace").props["data-button-variant"], "approved");
  browser.focus();
  assert.equal(h.requests.length, 3);
  h.requests.at(-1).reject(new Error("Network unavailable"));
  tree = await h.settle();
  assert.match(renderToStaticMarkup(tree), />Awaiting mentor and III Cell</);
  assert.doesNotMatch(renderToStaticMarkup(tree), />Approved</);
});

for (const scope of ["mentor", "admin"]) {
  const ownDecision = scope === "mentor" ? "mentorDecision" : "iiicDecision";
  const otherDecision = scope === "mentor" ? "iiicDecision" : "mentorDecision";

  test(`${scope} approval becomes white and disabled only after the server confirms final approval and remains so after reopening`, async () => {
    const h = harness({ scope });
    let tree = await h.loaded({ ...location, status: "PENDING", [ownDecision]: null });
    const pending = button(tree, "Approve workplace");
    assert.equal(pending.props.disabled, false);
    assert.notEqual(pending.props["data-button-variant"], "approved");
    h.click(tree, "Approve workplace");
    const review = h.requests.at(-1);
    assert.equal(review.url, `/${scope}/students/1/internship/location/review`);
    assert.deepEqual(review.body, { revision: 4, decision: "APPROVE", remarks: null });
    tree = h.render();
    assert.equal(button(tree, "Approve workplace").props.disabled, true);
    assert.notEqual(button(tree, "Approve workplace").props["data-button-variant"], "approved");
    review.resolve(location);
    tree = await h.settle();
    const approved = button(tree, "Approved");
    assert.equal(approved.props["data-button-variant"], "approved");
    assert.equal(approved.props.disabled, true);
    assert.match(renderToStaticMarkup(tree), /Both the assigned mentor and III Cell have approved this workplace/);
    const requestCount = h.requests.length;
    assert.equal(h.click(tree, "Approved"), false);
    assert.equal(h.requests.length, requestCount);
    h.close();

    const reopened = harness({ scope });
    tree = await reopened.loaded(location);
    assert.equal(button(tree, "Approved").props["data-button-variant"], "approved");
    assert.equal(button(tree, "Approved").props.disabled, true);
    assert.equal(reopened.requests.length, 1);
    reopened.close();
  });

  test(`${scope} partial approval keeps the active pending button and identifies the remaining reviewer after posting and reopening`, async () => {
    const h = harness({ scope });
    let tree = await h.loaded({ ...location, status: "PENDING", mentorDecision: null, iiicDecision: null });
    h.click(tree, "Approve workplace");
    const partial = { ...location, status: "PENDING", [ownDecision]: "APPROVE", [otherDecision]: null };
    h.requests.at(-1).resolve(partial);
    tree = await h.settle();
    assert.equal(button(tree, "Approve workplace").props.disabled, false);
    assert.notEqual(button(tree, "Approve workplace").props["data-button-variant"], "approved");
    assert.match(renderToStaticMarkup(tree), scope === "mentor" ? /Awaiting III Cell/ : /Awaiting mentor/);
    assert.doesNotMatch(renderToStaticMarkup(tree), />Approved</);
    h.close();

    const reopened = harness({ scope });
    tree = await reopened.loaded(partial);
    assert.equal(button(tree, "Approve workplace").props.disabled, false);
    assert.notEqual(button(tree, "Approve workplace").props["data-button-variant"], "approved");
    reopened.close();
  });

  test(`${scope} failed approval cannot turn the pending button white`, async () => {
    const h = harness({ scope });
    let tree = await h.loaded({ ...location, status: "PENDING", [ownDecision]: null });
    h.click(tree, "Approve workplace");
    h.requests.at(-1).reject({ response: { data: { message: "Approval was not saved. Try again." } } });
    tree = await h.settle();
    assert.match(renderToStaticMarkup(tree), /Approval was not saved[.] Try again[.]/);
    assert.equal(button(tree, "Approve workplace").props.disabled, false);
    assert.notEqual(button(tree, "Approve workplace").props["data-button-variant"], "approved");
    assert.doesNotMatch(renderToStaticMarkup(tree), />Approved</);
    h.close();
  });

  test(`${scope} can review rejected workplaces and pending approvals that are invalid for the current reviewers`, async () => {
    const h = harness({ scope });
    let tree = await h.loaded({ ...location, status: "PENDING" });
    assert.match(renderToStaticMarkup(tree), /Approval needs review/);
    assert.equal(button(tree, "Approve workplace").props.disabled, false);
    assert.notEqual(button(tree, "Approve workplace").props["data-button-variant"], "approved");
    h.click(tree, "Approve workplace");
    assert.equal(h.requests.at(-1).body.decision, "APPROVE");
    h.requests.at(-1).resolve({ ...location, status: "REJECTED", [otherDecision]: "REJECT" });
    tree = await h.settle();
    assert.match(renderToStaticMarkup(tree), />Rejected</);
    assert.equal(button(tree, "Approve workplace").props.disabled, false);
    assert.notEqual(button(tree, "Approve workplace").props["data-button-variant"], "approved");
    h.close();
  });
}

test("a pending background response cannot overwrite a newer review response", async t => {
  const browser = browserEvents(t);
  const h = harness();
  browser.cleanup(() => h.close());
  const pending = { ...location, status: "PENDING", iiicDecision: null };
  let tree = await h.loaded(pending);
  browser.focus();
  assert.equal(h.requests.length, 2);
  const oldRead = h.requests.at(-1);
  h.click(tree, "Approve workplace");
  const review = h.requests.at(-1);
  assert.equal(review.method, "post");
  review.resolve(location);
  tree = await h.settle();
  assert.match(renderToStaticMarkup(tree), />Approved</);
  oldRead.resolve(pending);
  tree = await h.settle();
  assert.match(renderToStaticMarkup(tree), />Approved</);
  assert.doesNotMatch(renderToStaticMarkup(tree), />Awaiting III Cell</);
});

test("a previous student's late review cannot replace the current student's record", async () => {
  const h = harness();
  let tree = await h.loaded({ ...location, status: "PENDING", iiicDecision: null });
  h.click(tree, "Approve workplace");
  const oldReview = h.requests.at(-1);
  h.render({ studentId: 2 });
  h.requests.at(-1).resolve({ ...location, workplaceName: "Current student's branch", status: "REJECTED", iiicDecision: "REJECT" });
  await h.settle();
  oldReview.resolve({ ...location, workplaceName: "Previous student's branch" });
  tree = await h.settle();
  const html = renderToStaticMarkup(tree);
  assert.match(html, /Current student&#x27;s branch/);
  assert.match(html, />Rejected</);
  assert.doesNotMatch(html, /Previous student|>Approved</);
  h.close();
});

test("automatic refresh preserves unsaved radius, review remarks and map correction drafts", async t => {
  const browser = browserEvents(t);
  const h = harness();
  browser.cleanup(() => h.close());
  let tree = await h.loaded();
  const initialCount = h.requests.length;
  radiusInput(tree).props.onChange({ target: { value: "250" } });
  tree = h.render();
  browser.focus(); browser.poll();
  assert.equal(h.requests.length, initialCount);
  assert.equal(radiusInput(h.render()).props.value, "250");
  radiusInput(tree).props.onChange({ target: { value: "200" } });
  tree = h.render();
  elements(tree).find(element => element.type === "textarea").props.onChange({ target: { value: "Review remarks in progress" } });
  tree = h.render();
  browser.focus(); browser.poll();
  assert.equal(h.requests.length, initialCount);
  assert.equal(elements(h.render()).find(element => element.type === "textarea").props.value, "Review remarks in progress");
  h.click(tree, "Correct registered workplace");
  tree = h.render();
  const draft = { name: "New entrance", placeId: null, address: "Draft branch address", latitude: 19.076, longitude: 72.8777 };
  h.picker(tree).onSelect(draft);
  h.render();
  browser.focus(); browser.poll();
  assert.equal(h.requests.length, initialCount);
  assert.deepEqual(h.picker(h.render()).value, draft);
});

test("attendance browser GPS and workplace are independent when browser GPS is missing", () => {
  const { AttendanceLocations } = load("AttendanceLocations", { "@/components/LocationMapLink": maps });
  const html = renderToStaticMarkup(React.createElement(AttendanceLocations, {
    evidence: { latitude: null, longitude: null, expectedLatitude: 21.125, expectedLongitude: 79.0875, workplaceAddress: "Registered branch", distanceMeters: null },
  }));
  assert.match(html, /Submission location unavailable/);
  assert.doesNotMatch(html, /Registered branch|workplace map|google[.]com/);
});
