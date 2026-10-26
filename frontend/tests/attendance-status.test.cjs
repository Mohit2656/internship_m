const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const ts = require("typescript");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");
function load(file, imports = {}) {
  const m = { exports: {} };
  new Function("require", "module", "exports", ts.transpileModule(fs.readFileSync(path.join(__dirname, "..", file), "utf8"), {
    compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
  }).outputText)(name => imports[name] || require(name), m, m.exports);
  return m.exports;
}
const attendance = load("lib/attendance.ts");
const submissionMaps = load("components/LocationMapLink.tsx");
const submissionLocations = load("components/AttendanceLocations.tsx", {"@/components/LocationMapLink": submissionMaps});
const locationVerification = load("components/LocationVerificationResult.tsx");

const flush = () => new Promise(resolve => setImmediate(resolve));
const record = status => ({ id: 42, attendanceDate: "2026-10-11", attendanceResult: "PRESENT", verificationStatus: "VERIFIED",
  livenessStatus: status, submissionType: "PHYSICAL_CAPTURE", serverSubmittedAt: "2026-10-11T10:00:00Z",
  evidence: { latitude: 18, longitude: 73, verificationReason: "GPS_ACCURACY_TOO_LOW", workplaceAddress: "Private historical workplace" } });

test("attendance labels use explicit liveness results, preserving absence and historical records", () => {
  for (const [status, expected] of [["VERIFIED", "Liveness verified"], ["FAILED", "Liveness not confirmed"],
    ["NOT_PERFORMED", "Liveness not performed"], ["HISTORICAL", "Historical attendance — liveness not reassessed"],
    [undefined, "Historical attendance — liveness not reassessed"]]) {
    const item = record(status);
    assert.equal(attendance.attendanceLabel(item), `Present — ${expected}`);
    assert.equal(attendance.attendanceLabel({ ...item, attendanceResult: "ABSENT" }), "Absent");
  }
  assert.equal(attendance.attendanceLabel({ ...record("VERIFIED"), verificationStatus: "UNVERIFIED" }), "Present — Liveness verified");
});

function renderStudent(item) {
  let cursor = 0;
  const hooks = { useEffect() {}, useRef() { return { current: null }; }, useState(initial) {
    const index = cursor++;
    return [index === 0 ? { attendance: item } : index === 3 ? false : initial, () => {}];
  }};
  const { TodayAttendanceCard } = load("components/TodayAttendanceCard.tsx", {
    react: hooks, "@/lib/api": {}, "@/components/AttendanceLocations": submissionLocations, "@/components/LocationVerificationResult": locationVerification, "@/lib/attendance": attendance, "@/lib/edge-ai": {}
  });
  return renderToStaticMarkup(React.createElement(TodayAttendanceCard));
}
// Mentor/admin attendance reports are exercised in liveness-reports.test.cjs.
for (const [status, expected] of [["VERIFIED", "Liveness verified"], ["FAILED", "Liveness not confirmed"],
  ["NOT_PERFORMED", "Liveness not performed"], ["HISTORICAL", "Historical attendance — liveness not reassessed"]]) {
  test(`student consistently display ${status} without location reasons`, () => {
    for (const html of [renderStudent(record(status))]) {
      assert.ok(html.includes(expected));
      assert.doesNotMatch(html, /Attendance unverified|Attendance verified|Private historical workplace|GPS_ACCURACY|Registered workplace|Attendance GPS map/);
    }
  });
}
test("student render ABSENT separately from liveness verification", () => {
  for (const html of [renderStudent({ ...record("VERIFIED"), attendanceResult: "ABSENT" })]) {
    assert.match(html, />Absent</); assert.doesNotMatch(html, /Present|Liveness verified/);
  }
});

test("automatic daily absences have no student submission or verification evidence labels", () => {
  const item = { ...record("NOT_APPLICABLE"), attendanceResult: "ABSENT", submissionType: "AUTO_ABSENT", automaticAbsent: true, evidence: null };
  assert.equal(attendance.isAutomaticAbsence(item), true);
  assert.equal(attendance.isAutomaticAbsence({ ...item, automaticAbsent: undefined }), true);
  for (const html of [renderStudent(item)]) {
    assert.match(html, />Absent</);
    assert.match(html, /[Aa]utomatically/);
    assert.doesNotMatch(html, /Submitted at|Last updated by student|Submission location unavailable|Liveness verified|Historical attendance/);
  }
});

test("the noon submission deadline closes at the exact server timestamp", () => {
  const today = { attendanceDate: "2026-10-19", attendance: null, submissionOpen: true, submissionDeadlineAt: "2026-10-19T06:30:00Z", attendanceZone: "Asia/Kolkata" };
  assert.equal(attendance.attendanceSubmissionOpen(today, Date.parse("2026-10-19T06:29:59.999Z")), true);
  assert.equal(attendance.attendanceSubmissionOpen(today, Date.parse("2026-10-19T06:30:00Z")), false);
  assert.equal(attendance.attendanceSubmissionOpen(today, Date.parse("2026-10-19T07:00:00Z")), false);
  assert.equal(attendance.attendanceSubmissionOpen({ ...today, submissionOpen: false }, Date.parse("2026-10-19T06:00:00Z")), false);
});

test("late permission overrides noon only on the attendance date in the configured time zone", () => {
  const today = { attendanceDate: "2026-10-19", attendance: null, submissionOpen: true, latePermissionEnabled: true, submissionDeadlineAt: "2026-10-19T06:30:00Z", attendanceZone: "Asia/Kolkata" };
  assert.equal(attendance.attendanceSubmissionOpen(today, Date.parse("2026-10-19T07:00:00Z")), true);
  assert.equal(attendance.attendanceSubmissionOpen(today, Date.parse("2026-10-19T18:29:59.999Z")), true);
  assert.equal(attendance.attendanceSubmissionOpen(today, Date.parse("2026-10-19T18:30:00Z")), false);
  assert.equal(attendance.attendanceSubmissionOpen({ ...today, latePermissionEnabled: false }, Date.parse("2026-10-19T07:00:00Z")), false);
  assert.equal(attendance.attendanceSubmissionOpen({ ...today, submissionOpen: false }, Date.parse("2026-10-19T07:00:00Z")), false);
});

function cameraHarness({ liveness = true, method = "PHYSICAL", today = {}, locationOutcome } = {}) {
  const states = [], refs = [], effects = []; let stateIndex = 0, refIndex = 0, effectIndex = 0;
  const pendingEffects = [], calls = [];
  const video = { videoWidth: 640, videoHeight: 480, async play() {} };
  const canvas = { getContext: () => ({ drawImage() {} }), toBlob: callback => callback(new Blob(["valid captured fixture"], { type: "image/jpeg" })) };
  const hooks = {
    useState(initial) { const i = stateIndex++; if (!(i in states)) states[i] = initial; return [states[i], value => { states[i] = typeof value === "function" ? value(states[i]) : value; }]; },
    useRef(initial) { const i = refIndex++; if (!(i in refs)) refs[i] = { current: i === 0 ? video : i === 1 ? canvas : initial }; return refs[i]; },
    useEffect(effect, dependencies) { const i = effectIndex++; if (!effects[i] || dependencies.some((value, n) => value !== effects[i][n])) { effects[i] = dependencies; pendingEffects.push(effect); } }
  };
  const api = {
    async get(url) { return { data: url.endsWith("/policy") ? { internshipMode: "OFFICE_REPORTING", verificationMethod: method, configurationReady: true } : { attendanceDate: "2026-10-11", attendance: null, ...today } }; },
    async post(url, body, config) { calls.push({ url, body, config }); return { data: url.endsWith("verification-session") ? { sessionId: "issued-by-server", challenge: "BLINK" } : { ...record(method === "BASIC" ? "NOT_PERFORMED" : "VERIFIED"), submittedLate: today.latePermissionEnabled === true, ...(locationOutcome ? { evidence: { locationVerificationStatus: locationOutcome, distanceMeters: 200, geofenceRadiusMeters: 100 } } : {}) } }; }
  };
  class Analyzer { async initialize() {} start(video, challenge, update) { if (liveness !== null) update({ faceCount: 1, faceDetected: true, singleFace: true, qualityPassed: true, livenessPassed: liveness }); } close() {} }
  const { TodayAttendanceCard } = load("components/TodayAttendanceCard.tsx", { react: hooks, "@/lib/api": { api },
    "@/components/AttendanceLocations": submissionLocations, "@/components/LocationVerificationResult": locationVerification, "@/lib/attendance": attendance, "@/lib/edge-ai": { EdgeAiAnalyzer: Analyzer, challengeLabel: () => "Blink", EDGE_AI_MODEL_VERSION: "existing-model" } });
  function render() { stateIndex = refIndex = effectIndex = 0; return TodayAttendanceCard(); }
  function nodes(type) { const result = []; function walk(node) { if (!node || typeof node !== "object") return; if (node.type === type) result.push(node); React.Children.forEach(node.props?.children, walk); } walk(render()); return result; }
  return { calls, button: label => nodes("button").find(node => node.props.children === label),
    summary: value => nodes("textarea")[0].props.onChange({ target: { value } }),
    html: () => renderToStaticMarkup(render()),
    async mount() { render(); pendingEffects.splice(0).forEach(effect => effect()); await flush(); },
    cancel() { const cancel = nodes("button").find(node => node.props.children === "Cancel"); cancel?.props.onClick(); }
  };
}
function navigatorWithoutLocation(t) {
  const original = Object.getOwnPropertyDescriptor(globalThis, "navigator"); let locationRequests = 0;
  Object.defineProperty(globalThis, "navigator", { configurable: true, value: {
    mediaDevices: { async getUserMedia() { return { getTracks: () => [{ stop() {} }] }; } },
    get geolocation() { locationRequests += 1; throw new Error("Location permission denied"); }
  }});
  t.after(() => { if (original) Object.defineProperty(globalThis, "navigator", original); else delete globalThis.navigator; });
  return () => locationRequests;
}

test("the student sees the configured noon deadline and cannot start submission after it", async t => {
  const originalNow = Date.now;
  let now = Date.parse("2026-10-19T06:29:59Z");
  Date.now = () => now; t.after(() => { Date.now = originalNow; });
  const h = cameraHarness({ today: { attendanceDate: "2026-10-19", submissionOpen: true, submissionDeadlineAt: "2026-10-19T06:30:00Z", attendanceZone: "Asia/Kolkata" } });
  await h.mount();
  assert.match(h.html(), /12:00 pm|12:00 PM/); assert.match(h.html(), /Asia\/Kolkata/);
  assert.equal(h.button("Mark Today's Attendance").props.disabled, false);
  now = Date.parse("2026-10-19T06:30:00Z");
  assert.equal(h.button("Mark Today's Attendance").props.disabled, true);
  h.button("Mark Today's Attendance").props.onClick(); await flush();
  assert.equal(h.calls.length, 0); assert.match(h.html(), /submission window has closed/);
});
test("attendance cannot submit before required location capture", async t => {
  const requests = navigatorWithoutLocation(t);
  const h = cameraHarness(); await h.mount(); h.button("Mark Today's Attendance").props.onClick(); await flush();
  await h.button("Capture Attendance").props.onClick(); await flush();
  assert.equal(h.button("Submit Attendance").props.disabled, true);
  h.button("Submit Attendance").props.onClick(); await flush();
  assert.equal(h.calls.filter(call => call.url.endsWith("/physical")).length, 0);
  assert.match(h.html(), /Capture your submission location/); assert.equal(requests(), 0); h.cancel();
});
for (const liveness of [false, null]) test(`${liveness === null ? "missing" : "failed"} liveness keeps capture disabled without submitting attendance`, async t => {
  const locationRequests = navigatorWithoutLocation(t);
  const h = cameraHarness({ liveness }); await h.mount(); h.button("Mark Today's Attendance").props.onClick(); await flush();
  const capture = h.button("Capture Attendance"); assert.equal(capture.props.disabled, true);
  capture.props.onClick(); await flush();
  assert.match(h.html(), /Complete face positioning and the liveness challenge/);
  assert.equal(h.calls.filter(call => !call.url.endsWith("verification-session")).length, 0);
  assert.equal(locationRequests(), 0); h.cancel();
});
test("basic attendance requires location and still records liveness not performed", async t => {
  const original = Object.getOwnPropertyDescriptor(globalThis, "navigator");
  Object.defineProperty(globalThis, "navigator", { configurable: true, value: { geolocation: { getCurrentPosition(success) { success({ coords: { latitude: 18, longitude: 73, accuracy: 10 } }); } } } });
  t.after(() => { if (original) Object.defineProperty(globalThis, "navigator", original); else delete globalThis.navigator; });
  const h = cameraHarness({ method: "BASIC" }); await h.mount();
  assert.equal(h.button("Mark Today's Attendance").props.disabled, true);
  h.button("Capture submission location (required)").props.onClick(); await flush();
  assert.equal(h.button("Mark Today's Attendance").props.disabled, false);
  h.button("Mark Today's Attendance").props.onClick(); await flush();
  assert.equal(h.calls.length, 1); assert.equal(h.calls[0].url, "/student/attendance/today");
  assert.deepEqual(h.calls[0].config.params, { latitude: 18, longitude: 73, accuracy: 10 });
  assert.match(h.html(), /Liveness not performed/); assert.doesNotMatch(h.html(), /Liveness verified/);
});

test("student display the same stored submission coordinates without requesting permission", t => {
  const requests = navigatorWithoutLocation(t);
  const item = { ...record("FAILED"), evidence: { latitude: 18.5204, longitude: 73.8567, gpsAccuracyMeters: 106, workplaceAddress: "Unrelated company address" } };
  for (const html of [renderStudent(item)]) {
    assert.match(html, /Latitude: 18[.]5204/); assert.match(html, /Longitude: 73[.]8567/); assert.match(html, /Reported GPS accuracy: 106 m/);
    assert.match(html, /query=18[.]5204%2C73[.]8567/); assert.match(html, /Open submission location in Google Maps/);
    assert.match(html, /target="_blank"/); assert.match(html, /noopener noreferrer/); assert.match(html, /Liveness not confirmed/);
    assert.doesNotMatch(html, /Unrelated company address|Registered workplace|geofence|radius/i);
  }
  assert.equal(requests(), 0);
});

test("missing, partial, invalid and nonnumeric stored coordinates are neutral; zero coordinates remain valid", () => {
  for (const evidence of [null, { latitude: null, longitude: null }, { latitude: 18, longitude: null }, { latitude: 91, longitude: 73 }, { latitude: 18, longitude: Infinity }, { latitude: "18", longitude: 73 }]) {
    const item = { ...record("VERIFIED"), evidence };
    for (const html of [renderStudent(item)]) {
      assert.match(html, /Submission location unavailable/); assert.doesNotMatch(html, /google[.]com\/maps/); assert.match(html, /Liveness verified/);
    }
  }
  for (const html of [renderStudent({ ...record("HISTORICAL"), evidence: { latitude: 0, longitude: 0, gpsAccuracyMeters: -1 } })]) {
    assert.match(html, /query=0%2C0/); assert.doesNotMatch(html, /Reported GPS accuracy/); assert.match(html, /Historical attendance/);
  }
});

for (const method of ["PHYSICAL", "REMOTE"]) test(`${method}: required browser capture sends the actual coordinates with liveness evidence`, async t => {
  const original = Object.getOwnPropertyDescriptor(globalThis, "navigator"); let requests = 0;
  Object.defineProperty(globalThis, "navigator", { configurable: true, value: {
    mediaDevices: { async getUserMedia() { return { getTracks: () => [{ stop() {} }] }; } },
    geolocation: { getCurrentPosition(success) { requests++; success({ coords: { latitude: 18.5204, longitude: 73.8567, accuracy: 12 } }); } }
  }});
  t.after(() => { if (original) Object.defineProperty(globalThis, "navigator", original); else delete globalThis.navigator; });
  const h = cameraHarness({ method }); await h.mount(); h.button("Mark Today's Attendance").props.onClick(); await flush();
  await h.button("Capture Attendance").props.onClick(); await flush(); assert.equal(requests, 0);
  h.button("Capture submission location (required)").props.onClick(); await flush(); assert.equal(requests, 1);
  if (method === "REMOTE") h.summary("Completed implementation and tested the attendance flow.");
  h.button("Submit Attendance").props.onClick(); await flush();
  const body = h.calls.find(call => call.url.endsWith(method === "REMOTE" ? "/remote" : "/physical")).body;
  assert.equal(body.get("latitude"), "18.5204"); assert.equal(body.get("longitude"), "73.8567"); assert.equal(body.get("accuracy"), "12");
  assert.equal(JSON.parse(await body.get("aiEvidence").text()).livenessPassed, true); assert.equal(body.has("workplace"), false); h.cancel();
});

for (const method of ["PHYSICAL", "REMOTE"]) test(`${method}: denied required location prevents submission`, async t => {
  const requests = navigatorWithoutLocation(t); const h = cameraHarness({ method }); await h.mount();
  h.button("Mark Today's Attendance").props.onClick(); await flush(); await h.button("Capture Attendance").props.onClick(); await flush();
  h.button("Capture submission location (required)").props.onClick(); await flush(); assert.equal(requests(), 1);
  assert.match(h.html(), /Submission location is required/); assert.equal(h.button("Submit Attendance").props.disabled, true);
  if (method === "REMOTE") h.summary("Completed implementation and tested the attendance flow.");
  h.button("Submit Attendance").props.onClick(); await flush();
  assert.equal(h.calls.filter(call => call.url.endsWith("/physical") || call.url.endsWith("/remote")).length, 0); h.cancel();
});

test("same-day late permission lets an automatically absent student complete the existing liveness flow", async t => {
  const originalNow = Date.now;
  Date.now = () => Date.parse("2026-10-19T07:00:00Z");
  t.after(() => { Date.now = originalNow; });
  const original = Object.getOwnPropertyDescriptor(globalThis, "navigator");
  Object.defineProperty(globalThis, "navigator", { configurable: true, value: {
    mediaDevices: { async getUserMedia() { return { getTracks: () => [{ stop() {} }] }; } },
    geolocation: { getCurrentPosition(success) { success({ coords: { latitude: 18.5204, longitude: 73.8567, accuracy: 12 } }); } }
  }});
  t.after(() => { if (original) Object.defineProperty(globalThis, "navigator", original); else delete globalThis.navigator; });
  const today = {
    attendanceDate: "2026-10-19", submissionOpen: true, latePermissionEnabled: true,
    submissionDeadlineAt: "2026-10-19T06:30:00Z", attendanceZone: "Asia/Kolkata",
    attendance: { ...record("NOT_APPLICABLE"), attendanceDate: "2026-10-19", attendanceResult: "ABSENT", automaticAbsent: true, submissionType: "AUTO_ABSENT", evidence: null }
  };
  const h = cameraHarness({ today }); await h.mount();
  assert.match(h.html(), /mentor has enabled late attendance submission/);
  assert.match(h.html(), /valid submission will update today&#x27;s automatic absence/);
  assert.equal(h.button("Mark Today's Attendance").props.disabled, false);
  assert.equal(h.calls.length, 0);
  h.button("Mark Today's Attendance").props.onClick(); await flush();
  assert.equal(h.calls[0].url, "/student/attendance/verification-session");
  assert.equal(h.button("Capture Attendance").props.disabled, false);
  await h.button("Capture Attendance").props.onClick(); await flush();
  h.button("Capture submission location (required)").props.onClick(); await flush();
  h.button("Submit Attendance").props.onClick(); await flush();
  const submitted = h.calls.find(call => call.url.endsWith("/physical"));
  assert.equal(JSON.parse(await submitted.body.get("aiEvidence").text()).livenessPassed, true);
  assert.match(h.html(), /Submitted late with faculty mentor permission/);
  assert.equal(h.button("Mark Today's Attendance"), undefined);
  h.cancel();
});

test("late permission leaves failed liveness blocked", async t => {
  const originalNow = Date.now;
  Date.now = () => Date.parse("2026-10-19T07:00:00Z");
  t.after(() => { Date.now = originalNow; });
  navigatorWithoutLocation(t);
  const h = cameraHarness({ liveness: false, today: {
    attendanceDate: "2026-10-19", submissionOpen: true, latePermissionEnabled: true,
    submissionDeadlineAt: "2026-10-19T06:30:00Z", attendanceZone: "Asia/Kolkata"
  }});
  await h.mount(); h.button("Mark Today's Attendance").props.onClick(); await flush();
  assert.equal(h.button("Capture Attendance").props.disabled, true);
  h.button("Capture Attendance").props.onClick(); await flush();
  assert.equal(h.calls.filter(call => !call.url.endsWith("verification-session")).length, 0);
  h.cancel();
});

test("GPS verification is displayed from the server independently of liveness, and legacy evidence is neutral", () => {
  for (const [status, label] of [["VERIFIED", "Workplace location verified"], ["OUTSIDE_RADIUS", "outside the permitted"],
    ["INSUFFICIENT_ACCURACY", "GPS accuracy is insufficient"], ["LOCATION_UNAVAILABLE", "Location could not be verified"],
    ["WORKPLACE_NOT_APPROVED", "Workplace approval is required"], ["STALE_LOCATION", "GPS capture is too old"]]) {
    const html = renderStudent({ ...record("VERIFIED"), evidence: { locationVerificationStatus: status, distanceMeters: 120, geofenceRadiusMeters: 100 } });
    assert.ok(html.includes(label)); assert.match(html, /Liveness verified/); assert.match(html, /Distance: 120 m/);
  }
  for (const evidence of [{ latitude: 18, longitude: 73, locationCheckPassed: true }, { locationVerificationStatus: "NOT_APPLICABLE" }, null]) {
    assert.doesNotMatch(renderStudent({ ...record("VERIFIED"), evidence }), /Workplace location verified|manual review required/);
  }
});

test("capture timestamp accompanies GPS and the result appears only after the backend responds", async t => {
  const original = Object.getOwnPropertyDescriptor(globalThis, "navigator");
  const timestamp = Date.now();
  Object.defineProperty(globalThis, "navigator", { configurable: true, value: {
    mediaDevices: { async getUserMedia() { return { getTracks: () => [{ stop() {} }] }; } },
    geolocation: { getCurrentPosition(success) { success({ timestamp, coords: { latitude: 18.52, longitude: 73.85, accuracy: 12 } }); } }
  }});
  t.after(() => { if (original) Object.defineProperty(globalThis, "navigator", original); else delete globalThis.navigator; });
  const h = cameraHarness({ locationOutcome: "OUTSIDE_RADIUS" });
  await h.mount(); h.button("Mark Today's Attendance").props.onClick(); await flush();
  await h.button("Capture Attendance").props.onClick(); await flush();
  h.button("Capture submission location (required)").props.onClick(); await flush();
  assert.doesNotMatch(h.html(), /Workplace location verified|outside the permitted/);
  h.button("Submit Attendance").props.onClick(); await flush();
  const body = h.calls.find(call => call.url.endsWith("/physical")).body;
  assert.equal(body.get("locationCapturedAt"), new Date(timestamp).toISOString());
  for (const forbidden of ["studentId", "internshipId", "distanceMeters", "locationVerificationStatus"]) assert.equal(body.has(forbidden), false);
  assert.match(h.html(), /outside the permitted workplace radius/); assert.match(h.html(), /Liveness verified/);
  h.cancel();
});
