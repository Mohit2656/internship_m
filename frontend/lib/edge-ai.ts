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
const FRONTAL_MIN = 0.38;
const FRONTAL_MAX = 0.62;
const TURN_LEFT_RATIO = 0.31;
const TURN_RIGHT_RATIO = 0.69;

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

function horizontalNoseRatio(landmarks: NormalizedLandmark[]) {
  const nose = landmarks[1]; const cheekA = landmarks[234]; const cheekB = landmarks[454];
  if (!nose || !cheekA || !cheekB) return 0.5;
  const minimum = Math.min(cheekA.x, cheekB.x);
  const width = Math.abs(cheekB.x - cheekA.x);
  return width < 0.01 ? 0.5 : (nose.x - minimum) / width;
}

export class EdgeAiAnalyzer {
  private landmarker: FaceLandmarker | null = null;
  private frameId: number | null = null;
  private lastAnalyzedAt = 0;
  private openSeen = false;
  private closedSeen = false;
  private frontalSeen = false;
  private passed = false;

  async initialize() {
    const { FilesetResolver, FaceLandmarker } = await import("@mediapipe/tasks-vision");
    const vision = await FilesetResolver.forVisionTasks("/mediapipe/wasm");
    this.landmarker = await FaceLandmarker.createFromOptions(vision, {
      baseOptions: { modelAssetPath: "/models/face_landmarker.task" },
      runningMode: "VIDEO",
      numFaces: 2,
      minFaceDetectionConfidence: 0.6,
      minFacePresenceConfidence: 0.6,
      minTrackingConfidence: 0.6,
      outputFaceBlendshapes: true
    });
  }

  start(video: HTMLVideoElement, challenge: AiChallenge,
      update: (state: EdgeAiFrameState) => void, failed: () => void) {
    if (!this.landmarker) throw new Error("Edge AI model is not initialized");
    const analyze = (timestamp: number) => {
      try {
        if (timestamp - this.lastAnalyzedAt >= ANALYSIS_INTERVAL_MS && video.readyState >= 2) {
          this.lastAnalyzedAt = timestamp;
          const result = this.landmarker!.detectForVideo(video, timestamp);
          const faceCount = result.faceLandmarks.length;
          if (faceCount === 0) update({ ...initialState });
          else if (faceCount > 1) update({ ...initialState, faceCount, faceDetected: true,
            guidance: "Multiple faces detected. Only one person should be visible." });
          else {
            const landmarks = result.faceLandmarks[0];
            const quality = qualityPassed(landmarks);
            if (quality && !this.passed) this.observeChallenge(result, landmarks, challenge);
            const guidance = !quality ? "Move closer and keep your entire face inside the frame."
              : this.passed ? "Liveness confirmed. You can capture attendance."
              : challenge === "BLINK" ? "Please blink naturally."
              : challenge === "TURN_HEAD_LEFT" ? "Turn your head slightly to the left."
              : "Turn your head slightly to the right.";
            update({ faceCount: 1, faceDetected: true, singleFace: true,
              qualityPassed: quality, livenessPassed: this.passed, guidance });
          }
        }
      } catch {
        failed(); return;
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
    if (ratio >= FRONTAL_MIN && ratio <= FRONTAL_MAX) this.frontalSeen = true;
    if (this.frontalSeen && challenge === "TURN_HEAD_LEFT" && ratio <= TURN_LEFT_RATIO) this.passed = true;
    if (this.frontalSeen && challenge === "TURN_HEAD_RIGHT" && ratio >= TURN_RIGHT_RATIO) this.passed = true;
  }

  close() {
    if (this.frameId !== null) cancelAnimationFrame(this.frameId);
    this.frameId = null;
    this.landmarker?.close();
    this.landmarker = null;
  }
}

export function challengeLabel(challenge: AiChallenge | undefined) {
  if (challenge === "BLINK") return "Please blink";
  if (challenge === "TURN_HEAD_LEFT") return "Turn your head slightly to the left";
  if (challenge === "TURN_HEAD_RIGHT") return "Turn your head slightly to the right";
  return "Preparing challenge…";
}

