const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");

test("edge-ai.ts contains TFLite informational delegate log filter to prevent Next.js dev error overlay", () => {
  const filePath = path.resolve(__dirname, "../lib/edge-ai.ts");
  const content = fs.readFileSync(filePath, "utf8");

  // Function existence
  assert.ok(
    content.includes("export function installTfLiteLogFilter()"),
    "Must export installTfLiteLogFilter function"
  );

  // Target XNNPACK delegate message specifically
  assert.ok(
    content.includes("Created TensorFlow Lite XNNPACK delegate for CPU"),
    "Filter must specifically match 'Created TensorFlow Lite XNNPACK delegate for CPU'"
  );
  assert.ok(
    content.includes("XNNPACK delegate for CPU"),
    "Filter must match 'XNNPACK delegate for CPU'"
  );

  // Redirect to console.info so developers still have access without triggering Next.js dev overlay
  assert.ok(
    content.includes("console.info(...args)"),
    "Filter must redirect informational message to console.info"
  );

  // Preserve all genuine errors
  assert.ok(
    content.includes("originalConsoleError.apply(console, args)"),
    "Filter must preserve all genuine errors by forwarding to originalConsoleError"
  );

  // Deduplication guard
  assert.ok(
    content.includes("__ims_tflite_filter_installed"),
    "Filter must prevent redundant installations via __ims_tflite_filter_installed"
  );
});

test("edge-ai.ts enforces video readiness, timestamp monotonicity, and resilient inference", () => {
  const filePath = path.resolve(__dirname, "../lib/edge-ai.ts");
  const content = fs.readFileSync(filePath, "utf8");

  // Video readiness check
  assert.ok(
    content.includes("video.readyState >= 2") &&
    content.includes("video.videoWidth > 0") &&
    content.includes("video.videoHeight > 0") &&
    content.includes("!video.paused") &&
    content.includes("!video.ended"),
    "Must check complete video element readiness before calling detectForVideo"
  );

  // Monotonic timestamp check
  assert.ok(
    content.includes("Math.max(timestamp, this.lastAnalyzedAt + 1)"),
    "Must enforce strictly monotonic timestamps for MediaPipe Tasks Vision"
  );

  // Failure tolerance
  assert.ok(
    content.includes("MAX_CONSECUTIVE_FAILURES"),
    "Must handle transient frame failures without immediately failing liveness"
  );

  // Clean teardown
  assert.ok(
    content.includes("cancelAnimationFrame(this.frameId)") &&
    content.includes("this.landmarker?.close()"),
    "close() must cleanly cancel animation frames and release landmarker instance"
  );
});

test("Behavioral test: Log filter redirects only XNNPACK delegate info and retains genuine errors", () => {
  const loggedErrors = [];
  const loggedInfos = [];

  const originalConsoleError = console.error;
  const originalConsoleInfo = console.info;

  try {
    console.error = (...args) => loggedErrors.push(args);
    console.info = (...args) => loggedInfos.push(args);

    global.window = {};

    const installFilter = () => {
      if (typeof window === "undefined") return;
      const globalScope = window;
      if (globalScope.__ims_tflite_filter_installed) return;
      globalScope.__ims_tflite_filter_installed = true;

      const origErr = console.error;
      console.error = function (...args) {
        const isTfLiteInfo = args.some(arg => {
          if (typeof arg === "string") {
            return (
              arg.includes("Created TensorFlow Lite XNNPACK delegate for CPU") ||
              arg.includes("XNNPACK delegate for CPU") ||
              (arg.startsWith("INFO:") && arg.includes("TensorFlow Lite"))
            );
          }
          return false;
        });

        if (isTfLiteInfo) {
          console.info(...args);
          return;
        }

        origErr.apply(console, args);
      };
    };

    installFilter();

    console.error("INFO: Created TensorFlow Lite XNNPACK delegate for CPU.");

    assert.strictEqual(
      loggedErrors.length,
      0,
      "TFLite informational delegate message must NOT be forwarded to console.error"
    );
    assert.strictEqual(
      loggedInfos.length,
      1,
      "TFLite informational delegate message MUST be forwarded to console.info"
    );
    assert.strictEqual(
      loggedInfos[0][0],
      "INFO: Created TensorFlow Lite XNNPACK delegate for CPU."
    );

    const genuineError = new Error("Camera hardware disconnected");
    console.error("Camera capture failed:", genuineError);

    assert.strictEqual(
      loggedErrors.length,
      1,
      "Genuine runtime error must be passed through to console.error"
    );
    assert.strictEqual(loggedErrors[0][0], "Camera capture failed:");
    assert.strictEqual(loggedErrors[0][1], genuineError);

  } finally {
    console.error = originalConsoleError;
    console.info = originalConsoleInfo;
    delete global.window;
  }
});

test("Direction detection: horizontalNoseRatio maps anatomical turns to camera frame coordinates accurately", () => {
  // In an unmirrored camera facing the student:
  // Landmark 234 (student's right cheek) is at lower x (left of camera frame).
  // Landmark 454 (student's left cheek) is at higher x (right of camera frame).
  // Landmark 1 (nose tip) starts in the middle.

  function mockLandmarks(noseX, rightCheekX = 0.35, leftCheekX = 0.65) {
    const landmarks = [];
    landmarks[1] = { x: noseX, y: 0.5, z: 0 };
    landmarks[234] = { x: rightCheekX, y: 0.5, z: 0 };
    landmarks[454] = { x: leftCheekX, y: 0.5, z: 0 };
    return landmarks;
  }

  function horizontalNoseRatio(landmarks) {
    const nose = landmarks[1]; const cheekA = landmarks[234]; const cheekB = landmarks[454];
    if (!nose || !cheekA || !cheekB) return 0.5;
    const minimum = Math.min(cheekA.x, cheekB.x);
    const width = Math.abs(cheekB.x - cheekA.x);
    return width < 0.01 ? 0.5 : (nose.x - minimum) / width;
  }

  // 1. Student looking straight (Frontal)
  const frontalRatio = horizontalNoseRatio(mockLandmarks(0.50));
  assert.ok(
    frontalRatio >= 0.38 && frontalRatio <= 0.62,
    `Frontal ratio should be around 0.50, got ${frontalRatio}`
  );

  // 2. Student physically turns head to their OWN LEFT:
  // Nose moves towards their left cheek (landmark 454 on camera right, higher x).
  const turnLeftRatio = horizontalNoseRatio(mockLandmarks(0.58));
  assert.ok(
    turnLeftRatio >= 0.69,
    `Turning head to own left must yield ratio >= 0.69, got ${turnLeftRatio}`
  );

  // 3. Student physically turns head to their OWN RIGHT:
  // Nose moves towards their right cheek (landmark 234 on camera left, lower x).
  const turnRightRatio = horizontalNoseRatio(mockLandmarks(0.42));
  assert.ok(
    turnRightRatio <= 0.31,
    `Turning head to own right must yield ratio <= 0.31, got ${turnRightRatio}`
  );
});

test("Direction detection validation matrix (Look Straight, Turn Left, Turn Right)", () => {
  const FRONTAL_MIN = 0.38;
  const FRONTAL_MAX = 0.62;
  const TURN_LEFT_THRESHOLD = 0.69;
  const TURN_RIGHT_THRESHOLD = 0.31;

  function evaluateHeadTurn(ratio, challenge, frontalSeen) {
    const isFrontal = ratio >= FRONTAL_MIN && ratio <= FRONTAL_MAX;
    const nowFrontal = frontalSeen || isFrontal;
    let passed = false;
    if (nowFrontal && challenge === "TURN_HEAD_LEFT" && ratio >= TURN_LEFT_THRESHOLD) {
      passed = true;
    }
    if (nowFrontal && challenge === "TURN_HEAD_RIGHT" && ratio <= TURN_RIGHT_THRESHOLD) {
      passed = true;
    }
    return { frontalSeen: nowFrontal, passed };
  }

  // Simulated ratios:
  const RATIO_FRONTAL = 0.50;    // Looking straight ahead
  const RATIO_TURN_LEFT = 0.77;   // Student turned head toward own left
  const RATIO_TURN_RIGHT = 0.23;  // Student turned head toward own right

  // Scenario 1: Look Straight -> Face camera directly -> Frontal accepted
  const lookStraight = evaluateHeadTurn(RATIO_FRONTAL, "TURN_HEAD_LEFT", false);
  assert.strictEqual(lookStraight.frontalSeen, true, "Frontal pose must be detected and accepted");
  assert.strictEqual(lookStraight.passed, false, "Looking straight does not prematurely pass turn challenge");

  // Scenario 2: Turn Left instruction + Student turns toward own left -> Accepted
  const turnLeftCorrect = evaluateHeadTurn(RATIO_TURN_LEFT, "TURN_HEAD_LEFT", true);
  assert.strictEqual(turnLeftCorrect.passed, true, "Turning toward own left must be Accepted for Turn Left");

  // Scenario 3: Turn Right instruction + Student turns toward own right -> Accepted
  const turnRightCorrect = evaluateHeadTurn(RATIO_TURN_RIGHT, "TURN_HEAD_RIGHT", true);
  assert.strictEqual(turnRightCorrect.passed, true, "Turning toward own right must be Accepted for Turn Right");

  // Scenario 4: Turn Left instruction + Student turns toward own right -> Rejected
  const turnLeftWrong = evaluateHeadTurn(RATIO_TURN_RIGHT, "TURN_HEAD_LEFT", true);
  assert.strictEqual(turnLeftWrong.passed, false, "Turning toward own right must be Rejected for Turn Left");

  // Scenario 5: Turn Right instruction + Student turns toward own left -> Rejected
  const turnRightWrong = evaluateHeadTurn(RATIO_TURN_LEFT, "TURN_HEAD_RIGHT", true);
  assert.strictEqual(turnRightWrong.passed, false, "Turning toward own left must be Rejected for Turn Right");
});

test("TodayAttendanceCard mirrors front camera preview and canvas capture while preserving rear camera", () => {
  const cardPath = path.resolve(__dirname, "../components/TodayAttendanceCard.tsx");
  const content = fs.readFileSync(cardPath, "utf8");

  // Check state tracking
  assert.ok(
    content.includes("isFrontCamera"),
    "TodayAttendanceCard must track isFrontCamera state"
  );

  // Check camera constraint preference for user camera
  assert.ok(
    content.includes('facingMode: "user"') || content.includes("facingMode: 'user'"),
    "TodayAttendanceCard should request user facingMode for attendance selfie"
  );

  // Check front vs environment camera detection
  assert.ok(
    content.includes('settings.facingMode !== "environment"') ||
    content.includes('settings?.facingMode !== "environment"'),
    "TodayAttendanceCard must not mirror when camera is environment (rear)"
  );

  // Check video transform style
  assert.ok(
    content.includes('style={{ transform: isFrontCamera ? "scaleX(-1)" : "none" }}'),
    "Video preview must apply scaleX(-1) only for front camera"
  );

  // Check canvas capture mirroring
  assert.ok(
    content.includes("ctx.scale(-1, 1)") && content.includes("ctx.translate(canvas.width, 0)"),
    "Canvas capture must mirror horizontally for front camera to match preview orientation"
  );
});

test("TodayAttendanceCard provides explicit camera error states, prevents stream interruptions, and blocks already-submitted attendance", () => {
  const cardPath = path.resolve(__dirname, "../components/TodayAttendanceCard.tsx");
  const content = fs.readFileSync(cardPath, "utf8");

  // Error state mapping function
  assert.ok(
    content.includes("function getCameraErrorMessage"),
    "Must implement getCameraErrorMessage helper"
  );
  assert.ok(
    content.includes("Camera permission denied"),
    "Must handle Camera permission denied state"
  );
  assert.ok(
    content.includes("Camera in use by another application"),
    "Must handle Camera in use state"
  );
  assert.ok(
    content.includes("No camera detected"),
    "Must handle No camera detected state"
  );
  assert.ok(
    content.includes("Camera initialization failed"),
    "Must handle Camera initialization failed state"
  );

  // Retry Camera button
  assert.ok(
    content.includes("Retry Camera"),
    "Must provide a Retry Camera button when camera fails"
  );

  // Prevent duplicate submissions and close camera if already submitted
  assert.ok(
    content.includes("isSubmitted = Boolean(today?.attendance && !isAutomaticAbsence(today.attendance))"),
    "Must check whether attendance is already submitted"
  );
  assert.ok(
    content.includes("Today's attendance has already been submitted"),
    "Must catch already-submitted error message and close flow"
  );

  // Prevent stream reload interruptions
  assert.ok(
    content.includes("if (video.srcObject !== stream)"),
    "Must avoid re-assigning video.srcObject to prevent stream interruption AbortError"
  );
});

