const { test } = require("node:test");
const assert = require("node:assert/strict");
require("./register-tsx.cjs");
const { captureAttendanceLocation, validReportedAccuracy } = require("../lib/attendance-location.ts");

function harness(t, maximumAccuracy = 100) {
  t.mock.timers.enable({ apis: ["setTimeout"] });
  const previous = Object.getOwnPropertyDescriptor(globalThis, "navigator");
  const requests = [], readings = [], results = [];
  Object.defineProperty(globalThis, "navigator", { configurable: true, value: { geolocation: {
    getCurrentPosition(success, error, options) { requests.push({ success, error, options }); }
  } } });
  t.after(() => { if (previous) Object.defineProperty(globalThis, "navigator", previous); else delete globalThis.navigator; });
  const capture = captureAttendanceLocation({ maximumAccuracy, onReading: sample => readings.push(sample), onComplete: result => results.push(result) });
  t.after(capture.cancel);
  const position = accuracy => ({ timestamp: 1793059200000, coords: { latitude: 0, longitude: 0, accuracy } });
  return { requests, readings, results, capture, position };
}

test("capture requests fresh high accuracy and keeps the actual decimal accuracy and timestamp", t => {
  const h = harness(t);
  assert.deepEqual(h.requests[0].options, { enableHighAccuracy: true, timeout: 8000, maximumAge: 0 });
  h.requests[0].success(h.position(12.345678));
  assert.equal(h.results[0].reason, "CAPTURED");
  assert.equal(h.results[0].sample.accuracy, 12.345678);
  assert.equal(h.results[0].sample.latitude, 0);
  assert.equal(h.results[0].sample.capturedAt, new Date(1793059200000).toISOString());
  t.mock.timers.tick(40000); assert.equal(h.requests.length, 1); assert.equal(h.results.length, 1);
});

test("poor first fix waits for a usable reading instead of immediately ending capture", t => {
  const h = harness(t, 25);
  h.requests[0].success(h.position(106));
  assert.equal(h.readings[0].accuracy, 106); assert.equal(h.results.length, 0);
  t.mock.timers.tick(2000);
  h.requests[1].success(h.position(20.25));
  assert.equal(h.results[0].sample.accuracy, 20.25); assert.equal(h.results[0].reason, "CAPTURED");
});

test("20-second deadline returns the best real poor reading and never claims it is usable", t => {
  const h = harness(t);
  h.requests[0].success(h.position(106)); t.mock.timers.tick(2000);
  h.requests[1].success(h.position(150));
  t.mock.timers.tick(18000);
  assert.equal(h.results[0].reason, "INSUFFICIENT_ACCURACY");
  assert.equal(h.results[0].sample.accuracy, 106);
  const count = h.requests.length; t.mock.timers.tick(40000); assert.equal(h.requests.length, count);
});

for (const accuracy of [NaN, Infinity, 0, -1, 0.001, 1000000]) test(`invalid accuracy ${accuracy} cannot be accepted as a usable fix`, t => {
  const h = harness(t);
  assert.equal(validReportedAccuracy(accuracy), false);
  h.requests[0].success(h.position(accuracy)); t.mock.timers.tick(20000);
  assert.equal(h.results[0].reason, "INVALID_ACCURACY");
  assert.ok(Object.is(h.results[0].sample.accuracy, accuracy));
});

test("permission denial stops immediately with no further GPS requests", t => {
  const h = harness(t); h.requests[0].error({ code: 1, message: "private browser detail" });
  assert.deepEqual(h.results, [{ sample: null, reason: "PERMISSION_DENIED" }]);
  t.mock.timers.tick(20000); assert.equal(h.requests.length, 1);
});

test("permission prompt or a missing callback cannot keep loading indefinitely", t => {
  const h = harness(t); t.mock.timers.tick(20000);
  assert.deepEqual(h.results, [{ sample: null, reason: "TIMEOUT" }]);
  h.requests[0].success(h.position(1)); assert.equal(h.results.length, 1);
});

test("timeout retries within the bounded capture window and can recover", t => {
  const h = harness(t); h.requests[0].error({ code: 3 }); t.mock.timers.tick(2000);
  h.requests[1].success(h.position(10)); assert.equal(h.results[0].reason, "CAPTURED");
});

test("cancel clears scheduled retries and ignores in-flight callbacks", t => {
  const h = harness(t); h.requests[0].success(h.position(1000));
  h.capture.cancel(); t.mock.timers.tick(40000); h.requests[0].success(h.position(1));
  assert.equal(h.requests.length, 1); assert.equal(h.results.length, 0); assert.equal(h.readings.length, 1);
});

test("remote work retains its existing no-geofence accuracy policy", t => {
  const h = harness(t, null); h.requests[0].success(h.position(2000));
  assert.equal(h.results[0].reason, "CAPTURED"); assert.equal(h.results[0].sample.accuracy, 2000);
});
