import type { FaceLandmarker, NormalizedLandmark } from "@mediapipe/tasks-vision";
import type { AiChallenge } from "@/lib/attendance";

export const EDGE_AI_MODEL_VERSION = "mediapipe-face-landmarker@1.0.1/thresholds-v1";

// Deterministic project-level thresholds. These verify capture participation,
// not identity, and intentionally avoid persisting landmarks or embeddings.
const ANALYSIS_INTERVAL_MS = 120;
const MIN_FACE_WIDTH = 0.20;
const MIN_FACE_HEIGHT = 0.25;
const FRAME_MARGIN = 0.02;
const EYE_OPEN_MAX = 0.35;
const EYE_CLOSED_MIN = 0.52;
export const FRONTAL_MIN = 0.38;
export const FRONTAL_MAX = 0.62;
// In an unmirrored camera frame facing the user:
// - Landmark 234 (subject's right cheek) is on the camera's left (lower x).
// - Landmark 454 (subject's left cheek) is on the camera's right (higher x).
// When the student physically turns their head toward their own LEFT:
//   Their nose moves toward the camera's right (higher x, closer to landmark 454),
//   so the ratio (nose - minCheek) / width increases (ratio >= TURN_LEFT_THRESHOLD).
// When the student physically turns their head toward their own RIGHT:
//   Their nose moves toward the camera's left (lower x, closer to landmark 234),
//   so the ratio (nose - minCheek) / width decreases (ratio <= TURN_RIGHT_THRESHOLD).
export const TURN_LEFT_THRESHOLD = 0.69;
export const TURN_RIGHT_THRESHOLD = 0.31;

export type EdgeAiFrameState = {
  faceCount: number;
  faceDetected: boolean;
  singleFace: boolean;
  qualityPassed: boolean;
  livenessPassed: boolean;
  guidance: string;
};

const initialState: EdgeAiFrameState = {
  faceCount: 0,
  faceDetected: false,
  singleFace: false,
  qualityPassed: false,
  livenessPassed: false,
  guidance: "Position your face inside the camera."
};

function qualityPassed(landmarks: NormalizedLandmark[]) {
  const xs = landmarks.map(point => point.x);
  const ys = landmarks.map(point => point.y);
  const minX = Math.min(...xs); const maxX = Math.max(...xs);
  const minY = Math.min(...ys); const maxY = Math.max(...ys);
  return maxX - minX >= MIN_FACE_WIDTH && maxY - minY >= MIN_FACE_HEIGHT
    && minX >= FRAME_MARGIN && maxX <= 1 - FRAME_MARGIN
    && minY >= FRAME_MARGIN && maxY <= 1 - FRAME_MARGIN;
}

function blendshapeScore(landmarkerResult: ReturnType<FaceLandmarker["detectForVideo"]>, name: string) {
  return landmarkerResult.faceBlendshapes[0]?.categories.find(category => category.categoryName === name)?.score ?? 0;
}

/**
 * Intercepts harmless informational logs emitted by TensorFlow Lite / MediaPipe WASM.
 * Emscripten routes all C++ stderr output to console.error, which Next.js development
 * server intercepts and surfaces as an unhandled error overlay.
 *
 * This filter safely redirects only the specific XNNPACK CPU delegate informational messages
 * to console.info while leaving all genuine errors, exceptions, and stack traces completely untouched.
 */
export function installTfLiteLogFilter(): void {
  if (typeof window === "undefined") return;
  const globalScope = window as unknown as { __ims_tflite_filter_installed?: boolean };
  if (globalScope.__ims_tflite_filter_installed) return;
  globalScope.__ims_tflite_filter_installed = true;

  const originalConsoleError = console.error;
  console.error = function (...args: unknown[]) {
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
      // Forward to console.info so developers can inspect it without triggering Next.js dev overlay
      console.info(...args);
      return;
    }

    originalConsoleError.apply(console, args);
  };
}

if (typeof window !== "undefined") {
  installTfLiteLogFilter();
}

export function horizontalNoseRatio(landmarks: NormalizedLandmark[]) {
  const nose = landmarks[1]; const cheekA = landmarks[234]; const cheekB = landmarks[454];
  if (!nose || !cheekA || !cheekB) return 0.5;
  const minimum = Math.min(cheekA.x, cheekB.x);
  const width = Math.abs(cheekB.x - cheekA.x);
  return width < 0.01 ? 0.5 : (nose.x - minimum) / width;
}

export function evaluateHeadTurn(
  ratio: number,
  challenge: AiChallenge,
  frontalSeen: boolean
): { frontalSeen: boolean; passed: boolean } {
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

export class EdgeAiAnalyzer {
  private landmarker: FaceLandmarker | null = null;
  private frameId: number | null = null;
  private lastAnalyzedAt = 0;
  private openSeen = false;
  private closedSeen = false;
  private frontalSeen = false;
  private passed = false;
  private initPromise: Promise<void> | null = null;
  private isClosed = false;

  async initialize(): Promise<void> {
    if (this.landmarker) return;
    if (this.initPromise) return this.initPromise;

    this.isClosed = false;
    installTfLiteLogFilter();

    this.initPromise = (async () => {
      const { FilesetResolver, FaceLandmarker } = await import("@mediapipe/tasks-vision");
      const vision = await FilesetResolver.forVisionTasks("/mediapipe/wasm");
      if (this.isClosed) return;

      this.landmarker = await FaceLandmarker.createFromOptions(vision, {
        baseOptions: { modelAssetPath: "/models/face_landmarker.task" },
        runningMode: "VIDEO",
        numFaces: 2,
        minFaceDetectionConfidence: 0.6,
        minFacePresenceConfidence: 0.6,
        minTrackingConfidence: 0.6,
        outputFaceBlendshapes: true
      });
    })();

    try {
      await this.initPromise;
    } finally {
      this.initPromise = null;
    }
  }

  start(
    video: HTMLVideoElement,
    challenge: AiChallenge,
    update: (state: EdgeAiFrameState) => void,
    failed: () => void
  ) {
    if (!this.landmarker) throw new Error("Edge AI model is not initialized");
    installTfLiteLogFilter();

    this.openSeen = false;
    this.closedSeen = false;
    this.frontalSeen = false;
    this.passed = false;
    this.lastAnalyzedAt = 0;

    let consecutiveFailures = 0;
    const MAX_CONSECUTIVE_FAILURES = 5;

    const analyze = (timestamp: number) => {
      if (!this.landmarker || this.isClosed) return;

      try {
        const isVideoReady =
          video &&
          video.readyState >= 2 &&
          video.videoWidth > 0 &&
          video.videoHeight > 0 &&
          !video.paused &&
          !video.ended;

        if (isVideoReady && (this.lastAnalyzedAt === 0 || timestamp - this.lastAnalyzedAt >= ANALYSIS_INTERVAL_MS)) {
          // Ensure strictly monotonically increasing timestamp for MediaPipe Tasks Vision
          const currentTimestamp = Math.max(timestamp, this.lastAnalyzedAt + 1);
          this.lastAnalyzedAt = currentTimestamp;

          let result: ReturnType<FaceLandmarker["detectForVideo"]>;
          try {
            result = this.landmarker.detectForVideo(video, currentTimestamp);
            consecutiveFailures = 0;
          } catch (inferErr) {
            consecutiveFailures += 1;
            if (!this.landmarker || this.isClosed) return;
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
              failed();
              return;
            }
            this.frameId = requestAnimationFrame(analyze);
            return;
          }

          const faceCount = result.faceLandmarks.length;
          if (faceCount === 0) {
            update({ ...initialState });
          } else if (faceCount > 1) {
            update({
              ...initialState,
              faceCount,
              faceDetected: true,
              guidance: "Multiple faces detected. Only one person should be visible."
            });
          } else {
            const landmarks = result.faceLandmarks[0];
            const quality = qualityPassed(landmarks);
            if (quality && !this.passed) this.observeChallenge(result, landmarks, challenge);
            const guidance = !quality
              ? "Move closer and keep your entire face inside the frame."
              : this.passed
              ? "Liveness confirmed. You can capture attendance."
              : challenge === "BLINK"
              ? "Please blink naturally."
              : challenge === "TURN_HEAD_LEFT"
              ? "Turn your head slightly to the left."
              : "Turn your head slightly to the right.";
            update({
              faceCount: 1,
              faceDetected: true,
              singleFace: true,
              qualityPassed: quality,
              livenessPassed: this.passed,
              guidance
            });
          }
        }
      } catch {
        if (!this.landmarker || this.isClosed) return;
        failed();
        return;
      }

      this.frameId = requestAnimationFrame(analyze);
    };

    this.frameId = requestAnimationFrame(analyze);
  }

  private observeChallenge(result: ReturnType<FaceLandmarker["detectForVideo"]>,
      landmarks: NormalizedLandmark[], challenge: AiChallenge) {
    if (challenge === "BLINK") {
      const left = blendshapeScore(result, "eyeBlinkLeft");
      const right = blendshapeScore(result, "eyeBlinkRight");
      const open = left < EYE_OPEN_MAX && right < EYE_OPEN_MAX;
      const closed = left > EYE_CLOSED_MIN && right > EYE_CLOSED_MIN;
      if (!this.openSeen && open) this.openSeen = true;
      else if (this.openSeen && closed) this.closedSeen = true;
      else if (this.closedSeen && open) this.passed = true;
      return;
    }
    const ratio = horizontalNoseRatio(landmarks);
    const evaluation = evaluateHeadTurn(ratio, challenge, this.frontalSeen);
    this.frontalSeen = evaluation.frontalSeen;
    if (evaluation.passed) this.passed = true;
  }

  close() {
    this.isClosed = true;
    if (this.frameId !== null) {
      cancelAnimationFrame(this.frameId);
      this.frameId = null;
    }
    try {
      this.landmarker?.close();
    } catch {
      // Ignore cleanup error if already closed
    }
    this.landmarker = null;
  }
}

export function challengeLabel(challenge: AiChallenge | undefined) {
  if (challenge === "BLINK") return "Please blink";
  if (challenge === "TURN_HEAD_LEFT") return "Turn your head slightly to the left";
  if (challenge === "TURN_HEAD_RIGHT") return "Turn your head slightly to the right";
  return "Preparing challenge…";
}

