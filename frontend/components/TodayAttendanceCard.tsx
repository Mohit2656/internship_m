"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { AxiosError } from "axios";
import { api, ApiError } from "@/lib/api";
import { AiVerificationEvidence, AiVerificationSession, Attendance, AttendancePolicy, HybridSchedule, TodayAttendance, attendanceLabel, attendanceSubmissionOpen, isAutomaticAbsence, livenessLabel } from "@/lib/attendance";
import { challengeLabel, EdgeAiAnalyzer, EdgeAiFrameState, EDGE_AI_MODEL_VERSION } from "@/lib/edge-ai";
import { AttendanceLocations, validSubmissionCoordinates } from "@/components/AttendanceLocations";
import { LocationVerificationResult } from "@/components/LocationVerificationResult";
import { LoadingSpinner } from "@/components/LoadingSpinner";
import { captureAttendanceLocation, accuracyDescription, usableAccuracy, type AttendanceLocationSample } from "@/lib/attendance-location";
import type { InternshipDetails } from "@/lib/internship-details";

function getCameraErrorMessage(cause: unknown): { status: string; message: string } {
  if (cause instanceof DOMException) {
    if (cause.name === "NotAllowedError" || cause.name === "PermissionDeniedError") {
      return {
        status: "Camera permission denied",
        message: "Camera permission was denied. Please allow camera access in your browser settings to verify attendance."
      };
    }
    if (cause.name === "NotReadableError" || cause.name === "TrackStartError") {
      return {
        status: "Camera in use by another application",
        message: "Camera is in use by another application. Please close other applications or browser tabs using your camera and retry."
      };
    }
    if (cause.name === "NotFoundError" || cause.name === "DevicesNotFoundError") {
      return {
        status: "No camera detected",
        message: "No camera detected. Please connect a working camera device to verify attendance."
      };
    }
    if (cause.name === "OverconstrainedError") {
      return {
        status: "Camera initialization failed",
        message: "The requested camera resolution or settings are not supported by your device."
      };
    }
  }
  const apiMessage = (cause as AxiosError<ApiError>)?.response?.data?.message;
  if (apiMessage) {
    return {
      status: "Camera initialization failed",
      message: apiMessage
    };
  }
  return {
    status: "Camera initialization failed",
    message: "Camera or Edge AI could not initialize. Check camera access and retry."
  };
}

function parseAttendanceError(cause: unknown): string {
  const axiosError = cause as AxiosError<ApiError>;
  const data = axiosError.response?.data;
  const status = axiosError.response?.status;
  const code = data?.code;
  const message = data?.message;

  if (code === "ATTENDANCE_ALREADY_SUBMITTED" || status === 409) {
    return "Attendance has already been submitted for this internship today.";
  }
  if (code === "ATTENDANCE_WINDOW_CLOSED") {
    return "Attendance submission is not permitted at this time (submission window has closed).";
  }
  if (code === "SUBMISSION_LOCATION_REQUIRED" || code === "LOCATION_REQUIRED") {
    return "Location verification failed: a valid GPS location is required. Allow location access and retry.";
  }
  if (code === "GPS_ACCURACY_TOO_POOR") {
    return "Location verification failed: GPS accuracy is insufficient. Please move outdoors or near a window and retry.";
  }
  if (code === "LOCATION_OUT_OF_BOUNDS" || code === "OUTSIDE_RADIUS" || code === "WORKPLACE_NOT_APPROVED") {
    return "Location verification failed: your current location could not be verified against the workplace zone.";
  }
  if (code === "INVALID_CAMERA_IMAGE" || code === "CAMERA_IMAGE_REQUIRED") {
    return "Attendance photograph could not be verified. Please capture a clear photo.";
  }
  if (code === "INVALID_DAILY_WORK_SUMMARY") {
    return "Please enter a valid work/activity description (20 to 1000 characters).";
  }
  if (code === "ATTENDANCE_NOT_ELIGIBLE") {
    return message || "Selected internship is not currently eligible for attendance submission.";
  }
  if (code === "STUDENT_REQUIRED") {
    return "Student authorization could not be verified. Please log in again.";
  }
  if (code === "MENTOR_NOT_ASSIGNED") {
    return "Your mentor assignment is pending. Please contact your coordinator.";
  }
  if (code === "AI_CHALLENGE_FAILED" || code === "LIVENESS_FAILED") {
    return "Liveness verification failed. Please follow the instructions and retry.";
  }
  if (!axiosError.response) {
    return "Network connection issue. Please check your internet connection and try again.";
  }
  if (message && message !== "An unexpected error occurred" && !message.toLowerCase().includes("exception") && !message.toLowerCase().includes("sql")) {
    return message;
  }
  return "Unable to submit attendance. Please try again.";
}

export function TodayAttendanceCard() {
  const [today, setToday] = useState<TodayAttendance | null>(null);
  const [policy, setPolicy] = useState<AttendancePolicy | null>(null);
  const [schedule, setSchedule] = useState<HybridSchedule | null>(null);
  const [loading, setLoading] = useState(true);
  const [internships, setInternships] = useState<InternshipDetails[]>([]);
  const [internshipId, setInternshipId] = useState<number|null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [flowOpen, setFlowOpen] = useState(false);
  const [attendanceMode, setAttendanceMode] = useState<"camera" | "upload">("camera");
  const [uploadedPhotoFile, setUploadedPhotoFile] = useState<File | null>(null);
  const [uploadedPhotoPreview, setUploadedPhotoPreview] = useState<string>("");
  const [cameraStatus, setCameraStatus] = useState("");
  const [photo, setPhoto] = useState<Blob | null>(null);
  const [photoUrl, setPhotoUrl] = useState("");
  const [dailyWorkSummary, setDailyWorkSummary] = useState("");
  const [aiSession, setAiSession] = useState<AiVerificationSession | null>(null);
  const [aiState, setAiState] = useState<EdgeAiFrameState | null>(null);
  const [aiError, setAiError] = useState("");
  const [error, setError] = useState("");
  const [submissionLocation, setSubmissionLocation] = useState<AttendanceLocationSample | null>(null);
  const [locationNotice, setLocationNotice] = useState("");
  const [locating, setLocating] = useState(false);
  const [forceExtraWork, setForceExtraWork] = useState(false);
  const [holidayModalOpen, setHolidayModalOpen] = useState(false);
  const [holidayReason, setHolidayReason] = useState("");
  const [holidaySubmitting, setHolidaySubmitting] = useState(false);
  const [holidaySuccess, setHolidaySuccess] = useState("");
  const [holidayError, setHolidayError] = useState("");
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const analyzerRef = useRef<EdgeAiAnalyzer | null>(null);
  const aiPassedRef = useRef(false);
  const challengeTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const locationRequestRef = useRef(0);
  const statusVersionRef = useRef(0);
  const locationCaptureRef = useRef<{ cancel: () => void } | null>(null);
  const [windowClosed, setWindowClosed] = useState(false);
  const [isFrontCamera, setIsFrontCamera] = useState(true);
  useEffect(()=>{api.get<InternshipDetails[]>("/student/internship/all").then(r=>{setInternships(r.data);setInternshipId(current=>current??r.data[0]?.id??null)}).catch(()=>{})},[]);

  useEffect(() => {
    if (!attendanceSubmissionOpen(today)) { setWindowClosed(true); stopCamera(); setLocating(false); setFlowOpen(false); return; }
    setWindowClosed(false);
    if (!today?.submissionDeadlineAt) return;
    const timer = setTimeout(() => { setWindowClosed(true); stopCamera(); setLocating(false); setFlowOpen(false); }, Math.min(2_147_483_647, Math.max(0, Date.parse(today.submissionDeadlineAt) - Date.now())));
    return () => clearTimeout(timer);
  }, [today]);

  const refreshStatus = useCallback((initial = false) => {
    const version = statusVersionRef.current;
    return Promise.all([
      api.get<TodayAttendance>("/student/attendance/today", { params: { internshipId: internshipId ?? undefined } }),
      api.get<AttendancePolicy>("/student/attendance/today/policy", { params: { internshipId: internshipId ?? undefined } })
    ]).then(([attendanceResponse, policyResponse]) => {
      if (version !== statusVersionRef.current) return;
      setToday(attendanceResponse.data); setPolicy(policyResponse.data);
      if (initial && policyResponse.data.internshipMode === "HYBRID") {
        void api.get<HybridSchedule>("/student/internship/hybrid-schedule")
          .then(response => { setSchedule(response.data); }).catch(() => { setSchedule(null); });
      }
    })
      .catch(() => { if (initial) setError("Today's attendance status could not be loaded."); })
      .finally(() => { if (initial) setLoading(false); });
  }, [internshipId]);

  useEffect(() => {
    let cancelled = false;
    void refreshStatus(true);
    const refresh = () => { if (!cancelled) void refreshStatus(false); };
    const timer = typeof window !== "undefined" ? window.setInterval(refresh, 30_000) : null;
    if (typeof window !== "undefined") window.addEventListener("focus", refresh);
    return () => {
      cancelled = true; stopCamera(); locationRequestRef.current += 1;
      if (typeof window !== "undefined") {
        window.removeEventListener("focus", refresh);
        if (timer !== null) window.clearInterval(timer);
      }
    };
  }, [refreshStatus]);

  useEffect(() => () => { if (photoUrl) URL.revokeObjectURL(photoUrl); }, [photoUrl]);

  useEffect(() => {
    const video = videoRef.current;
    const stream = streamRef.current;
    if (!flowOpen || photo || !video || !stream) return;
    if (video.srcObject !== stream) {
      video.srcObject = stream;
    }
    if (video.paused) {
      void video.play().catch(err => {
        if (err instanceof DOMException && err.name === "AbortError") return;
        setError("The camera preview could not be started.");
      });
    }
  }, [flowOpen, photo]);

  useEffect(() => {
    const count = today?.submissionsCount ?? (today?.todaySubmissions?.length ?? (today?.attendance && !isAutomaticAbsence(today.attendance) ? 1 : 0));
    const maxDaily = today?.maxDailySubmissions ?? 2;
    if (count >= maxDaily && flowOpen) {
      stopCamera();
      setFlowOpen(false);
    }
  }, [today, flowOpen]);

  function stopCamera() {
    locationCaptureRef.current?.cancel(); locationCaptureRef.current = null;
    locationRequestRef.current += 1;
    if (videoRef.current) {
      try {
        videoRef.current.pause();
        videoRef.current.srcObject = null;
      } catch {
        // Safe reset
      }
    }
    streamRef.current?.getTracks().forEach(track => track.stop());
    streamRef.current = null;
    analyzerRef.current?.close(); analyzerRef.current = null;
    if (challengeTimeoutRef.current) clearTimeout(challengeTimeoutRef.current);
    challengeTimeoutRef.current = null;
  }

  async function videoElement() {
    for (let attempt = 0; attempt < 30; attempt += 1) {
      if (videoRef.current) return videoRef.current;
      await new Promise<void>(resolve => {
        if (typeof requestAnimationFrame !== "undefined") {
          requestAnimationFrame(() => resolve());
        } else {
          setTimeout(resolve, 16);
        }
      });
    }
    throw new Error("Camera preview did not initialize");
  }

  async function startCamera(preserveLocation = false) {
    const count = today?.submissionsCount ?? (today?.todaySubmissions?.length ?? (today?.attendance && !isAutomaticAbsence(today.attendance) ? 1 : 0));
    const maxDaily = today?.maxDailySubmissions ?? 2;
    if (count >= maxDaily) {
      stopCamera();
      setFlowOpen(false);
      setError("All 2 daily attendance submissions have already been recorded for today.");
      return;
    }
    if (!attendanceSubmissionOpen(today)) {
      setWindowClosed(true);
      setError("Today's attendance submission is not available.");
      return;
    }
    const isLocationValid = Boolean(
      submissionLocation &&
      validSubmissionCoordinates(submissionLocation.latitude, submissionLocation.longitude) &&
      (policy?.verificationMethod === "REMOTE" || typeof policy?.maxGpsAccuracyMeters !== "number" || usableAccuracy(submissionLocation.accuracy, policy.maxGpsAccuracyMeters))
    );
    if (!preserveLocation || !isLocationValid) {
      locationRequestRef.current += 1;
      setSubmissionLocation(null);
      setLocationNotice("");
      setLocating(false);
    }
    stopCamera(); setError(""); setAiError(""); setFlowOpen(true); setPhoto(null);
    setAiSession(null); setAiState(null); aiPassedRef.current = false;
    setCameraStatus("Preparing server challenge…");
    if (!navigator.mediaDevices?.getUserMedia) {
      setCameraStatus("No camera detected");
      setError("Camera access is unavailable. Open the application at http://localhost:3000 in a supported browser.");
      return;
    }
    try {
      const sessionResponse = await api.post<AiVerificationSession>("/student/attendance/verification-session",null,{params:{internshipId:internshipId??undefined}});
      setAiSession(sessionResponse.data);
      setCameraStatus("Requesting camera permission…");
      let stream: MediaStream;
      try {
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "user" }, audio: false });
      } catch {
        stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false });
      }
      streamRef.current = stream;
      const videoTrack = stream.getVideoTracks?.()[0] ?? stream.getTracks?.()[0];
      const settings = videoTrack?.getSettings ? videoTrack.getSettings() : undefined;
      const isFront = settings?.facingMode ? settings.facingMode !== "environment" : true;
      setIsFrontCamera(isFront);
      setCameraStatus("Loading camera preview…");
      const video = await videoElement();
      if (video.srcObject !== stream) {
        video.srcObject = stream;
      }
      try {
        await video.play();
      } catch (err) {
        if (!(err instanceof DOMException && err.name === "AbortError")) {
          throw err;
        }
      }
      setCameraStatus("Loading private on-device face analysis…");
      const analyzer = new EdgeAiAnalyzer(); analyzerRef.current = analyzer;
      await analyzer.initialize();
      analyzer.start(video, sessionResponse.data.challenge, state => {
        aiPassedRef.current = state.livenessPassed;
        setAiState(state);
      }, () => setAiError("Face analysis stopped unexpectedly. Retry edge verification."));
      challengeTimeoutRef.current = setTimeout(() => {
        if (!aiPassedRef.current) setAiError("Could not confirm liveness in time. Please retry edge verification.");
      }, 30_000);
      setCameraStatus("Camera ready");
    } catch (cause) {
      const apiMessage = (cause as AxiosError<ApiError>)?.response?.data?.message;
      if (apiMessage && apiMessage.toLowerCase().includes("already been submitted")) {
        stopCamera();
        setFlowOpen(false);
        setError("Today's attendance has already been submitted.");
        void refreshStatus();
        return;
      }
      const { status, message } = getCameraErrorMessage(cause);
      setCameraStatus(status);
      setError(message);
      stopCamera();
    }
  }

  async function capture() {
    const video = videoRef.current; const canvas = canvasRef.current;
    if (!video || !canvas || video.videoWidth === 0 || !aiState?.livenessPassed || !aiState.qualityPassed) {
      setError("Complete face positioning and the liveness challenge before capturing attendance."); return;
    }
    canvas.width = video.videoWidth; canvas.height = video.videoHeight;
    const ctx = canvas.getContext("2d");
    if (isFrontCamera && ctx && typeof ctx.save === "function") {
      ctx.save();
      ctx.translate(canvas.width, 0);
      ctx.scale(-1, 1);
      ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
      ctx.restore();
    } else {
      ctx?.drawImage(video, 0, 0, canvas.width, canvas.height);
    }
    const captured = await new Promise<Blob | null>(resolve => canvas.toBlob(resolve, "image/jpeg", 0.85));
    if (!captured) { setError("The camera image could not be captured."); return; }
    stopCamera();
    setPhoto(captured); setPhotoUrl(URL.createObjectURL(captured)); setCameraStatus("Photo captured");
  }

  function appendAiEvidence(form: FormData) {
    if (!aiSession || !aiState) return;
    const evidence: AiVerificationEvidence = {
      sessionId: aiSession.sessionId,
      challenge: aiSession.challenge,
      faceCount: aiState.faceCount,
      qualityPassed: aiState.qualityPassed,
      livenessPassed: aiState.livenessPassed,
      modelVersion: EDGE_AI_MODEL_VERSION
    };
    form.append("aiEvidence", new Blob([JSON.stringify(evidence)], { type: "application/json" }), "ai-evidence.json");
  }

  function includeSubmissionLocation() {
    locationCaptureRef.current?.cancel();
    const request = ++locationRequestRef.current;
    setLocating(true); setSubmissionLocation(null); setLocationNotice("");
    const maximum = policy?.maxGpsAccuracyMeters;
    const remoteCapture = policy?.verificationMethod === "REMOTE";
    if (!remoteCapture && (typeof maximum !== "number" || !Number.isFinite(maximum) || maximum <= 0)) {
      setLocating(false); setLocationNotice("The server's GPS accuracy limit is unavailable. Reload the dashboard and retry capture."); return;
    }
    locationCaptureRef.current = captureAttendanceLocation({
      maximumAccuracy: remoteCapture ? null : maximum!,
      onReading(sample) {
        if (request !== locationRequestRef.current) return;
        setLocationNotice(`Best reported GPS accuracy: ${accuracyDescription(sample.accuracy)}. Waiting up to 20 seconds for a usable reading${remoteCapture ? "" : ` (required: ${maximum} m or better)`}…`);
      },
      onComplete({ sample, reason }) {
        if (request !== locationRequestRef.current) return;
        setLocating(false); setSubmissionLocation(sample);
        if (reason === "CAPTURED" && sample) {
          setLocationNotice(`Submission location captured. Reported GPS accuracy: ${accuracyDescription(sample.accuracy)}.${remoteCapture ? " Workplace geofencing is not required for remote attendance." : ` Required: ${maximum} m or better. The server determines final verification.`}`);
        } else if (sample) {
          setLocationNotice(`Reported GPS accuracy: ${accuracyDescription(sample.accuracy)}; required: ${maximum} m or better. Move outdoors or near a window, enable precise device location, and retry GPS capture. A phone with GPS may provide a better reading. Submitting this reading will require location review; attendance and liveness rules are unchanged.`);
        } else {
          const detail = reason === "PERMISSION_DENIED" ? "Location permission was denied. Allow precise location in browser/device settings" : reason === "TIMEOUT" ? "Location capture timed out. Move somewhere with a clearer GPS signal" : "Your device location is unavailable. Check location services";
          setLocationNotice(`Submission location is required. ${detail}, then retry capture.`);
        }
      }
    });
  }

  function appendSubmissionLocation(form: FormData) {
    if (!submissionLocation) return;
    form.append("latitude", String(submissionLocation.latitude));
    form.append("longitude", String(submissionLocation.longitude));
    if (Number.isFinite(submissionLocation.accuracy) && submissionLocation.accuracy >= 0)
      form.append("accuracy", String(submissionLocation.accuracy));
    if (submissionLocation.capturedAt) form.append("locationCapturedAt", submissionLocation.capturedAt);
  }

  function recordSubmittedAttendance(data: Attendance) {
    setToday(current => {
      const base = current?.todaySubmissions && current.todaySubmissions.length > 0
        ? current.todaySubmissions
        : (current?.attendance && !isAutomaticAbsence(current.attendance) ? [current.attendance] : []);
      const filtered = base.filter(item => item.id !== data.id);
      const next = [...filtered, data];
      const maxDaily = current?.maxDailySubmissions ?? 2;
      return {
        ...current,
        attendanceDate: data.attendanceDate,
        attendance: data,
        todaySubmissions: next,
        submissionsCount: next.length,
        maxDailySubmissions: maxDaily,
        submissionOpen: next.length < maxDaily,
      };
    });
  }

  async function submitBasic() {
    if (!attendanceSubmissionOpen(today)) { setWindowClosed(true); setError("Today's daily attendance limit has been reached."); return; }
    if (policy?.verificationMethod !== "BASIC" && policy?.verificationMethod !== "UNAVAILABLE") {
      setError("Use today's required attendance evidence flow.");
      return;
    }
    if (!submissionLocation || !validSubmissionCoordinates(submissionLocation.latitude, submissionLocation.longitude)) {
      setError("Capture your submission location before submitting attendance."); return;
    }
    setSubmitting(true); setError("");
    const submittedAt = new Date().toISOString();
    try {
      const { data } = await api.post<Attendance>("/student/attendance/today", null, {
        params: {
          ...(internshipId ? { internshipId } : {}),
          latitude: submissionLocation.latitude,
          longitude: submissionLocation.longitude,
          ...(Number.isFinite(submissionLocation.accuracy) && submissionLocation.accuracy >= 0 ? { accuracy: submissionLocation.accuracy } : {}),
          ...(submissionLocation.capturedAt ? { locationCapturedAt: submissionLocation.capturedAt } : {}),
          submittedAt,
        }
      });
      statusVersionRef.current += 1;
      recordSubmittedAttendance(data);
      stopCamera(); setFlowOpen(false);
    } catch (cause) {
      setError(parseAttendanceError(cause));
    } finally { setSubmitting(false); }
  }

  async function submitUploadedPhoto() {
    if (!attendanceSubmissionOpen(today)) { setWindowClosed(true); setError("Today's daily attendance limit has been reached."); return; }
    if (!uploadedPhotoFile) { setError("Please select a photograph to upload."); return; }
    setSubmitting(true); setError("");
    const submittedAt = new Date().toISOString();
    const form = new FormData();
    form.append("photo", uploadedPhotoFile);
    form.append("submittedAt", submittedAt);
    if (internshipId) form.append("internshipId", String(internshipId));
    try {
      const { data } = await api.post<Attendance>("/student/attendance/today/upload", form, {
        headers: { "Content-Type": "multipart/form-data" },
        params: { ...(internshipId ? { internshipId } : {}), submittedAt },
      });
      statusVersionRef.current += 1;
      recordSubmittedAttendance(data);
      setUploadedPhotoFile(null);
      setUploadedPhotoPreview("");
      setFlowOpen(false);
    } catch (cause) {
      setError(parseAttendanceError(cause));
    } finally {
      setSubmitting(false);
    }
  }

  async function submitPhysical() {
    if (!attendanceSubmissionOpen(today)) { setWindowClosed(true); setError("Today's daily attendance limit has been reached."); return; }
    if (!photo) return;
    if (!submissionLocation || !validSubmissionCoordinates(submissionLocation.latitude, submissionLocation.longitude)) {
      setError("Capture your submission location before submitting attendance."); return;
    }
    setSubmitting(true); setError("");
    const submittedAt = new Date().toISOString();
    const form = new FormData();
    form.append("photo", photo, "attendance-capture.jpg");
    form.append("submittedAt", submittedAt);
    appendAiEvidence(form);
    appendSubmissionLocation(form);
    if(internshipId) form.append("internshipId",String(internshipId));
    try {
      const { data } = await api.post<Attendance>("/student/attendance/today/physical", form, {
        headers: { "Content-Type": "multipart/form-data" },
        params: {
          ...(internshipId ? { internshipId } : {}),
          ...(submissionLocation ? {
            latitude: submissionLocation.latitude,
            longitude: submissionLocation.longitude,
            ...(Number.isFinite(submissionLocation.accuracy) && submissionLocation.accuracy >= 0 ? { accuracy: submissionLocation.accuracy } : {}),
            ...(submissionLocation.capturedAt ? { locationCapturedAt: submissionLocation.capturedAt } : {})
          } : {}),
          submittedAt,
        },
      });
      statusVersionRef.current += 1;
      recordSubmittedAttendance(data);
      stopCamera();
      setFlowOpen(false);
    } catch (cause) {
      setError(parseAttendanceError(cause));
    } finally { setSubmitting(false); }
  }

  async function submitRemote() {
    if (!attendanceSubmissionOpen(today)) { setWindowClosed(true); setError("Today's daily attendance limit has been reached."); return; }
    if (!photo) return;
    const summary = dailyWorkSummary.trim();
    if (summary.length < 20 || summary.length > 1000) {
      setError("Daily work summary must contain between 20 and 1000 characters."); return;
    }
    if (!submissionLocation || !validSubmissionCoordinates(submissionLocation.latitude, submissionLocation.longitude)) {
      setError("Capture your submission location before submitting attendance."); return;
    }
    setSubmitting(true); setError("");
    const submittedAt = new Date().toISOString();
    const form = new FormData();
    form.append("photo", photo, "attendance-capture.jpg");
    form.append("submittedAt", submittedAt);
    appendAiEvidence(form);
    appendSubmissionLocation(form);
    form.append("dailyWorkSummary", summary);
    if(internshipId) form.append("internshipId",String(internshipId));
    try {
      const { data } = await api.post<Attendance>("/student/attendance/today/remote", form, {
        headers: { "Content-Type": "multipart/form-data" },
        params: {
          ...(internshipId ? { internshipId } : {}),
          dailyWorkSummary: summary,
          ...(submissionLocation ? {
            latitude: submissionLocation.latitude,
            longitude: submissionLocation.longitude,
            ...(Number.isFinite(submissionLocation.accuracy) && submissionLocation.accuracy >= 0 ? { accuracy: submissionLocation.accuracy } : {}),
            ...(submissionLocation.capturedAt ? { locationCapturedAt: submissionLocation.capturedAt } : {})
          } : {}),
          submittedAt,
        },
      });
      statusVersionRef.current += 1;
      recordSubmittedAttendance(data);
      stopCamera();
      setFlowOpen(false);
      setDailyWorkSummary("");
    } catch (cause) {
      setError(parseAttendanceError(cause));
    } finally { setSubmitting(false); }
  }

  async function submitHoliday() {
    if (!holidayReason.trim()) {
      setHolidayError("Please provide a reason for the company holiday.");
      return;
    }
    setHolidaySubmitting(true);
    setHolidayError("");
    try {
      await api.post("/student/attendance/exceptions/holiday", {
        internshipId: internshipId ?? undefined,
        date: today?.attendanceDate,
        reason: holidayReason.trim()
      });
      setHolidaySuccess("Company holiday request submitted. It is pending faculty mentor approval.");
      setHolidayModalOpen(false);
      setHolidayReason("");
      if (internshipId) {
        api.get<AttendancePolicy>("/student/attendance/today/policy", { params: { internshipId } })
          .then(({ data }) => setPolicy(data))
          .catch(() => {});
      }
    } catch (cause: unknown) {
      const msg = (cause as AxiosError<ApiError>)?.response?.data?.message || "Failed to submit holiday request.";
      setHolidayError(msg);
    } finally {
      setHolidaySubmitting(false);
    }
  }

  const attendance = today?.attendance;
  const physical = policy?.verificationMethod === "PHYSICAL";
  const remote = policy?.verificationMethod === "REMOTE";
  const poorAccuracy = Boolean(submissionLocation && !remote && typeof policy?.maxGpsAccuracyMeters === "number" && !usableAccuracy(submissionLocation.accuracy, policy.maxGpsAccuracyMeters));
  const hasValidLocation = Boolean(
    submissionLocation &&
    validSubmissionCoordinates(submissionLocation.latitude, submissionLocation.longitude) &&
    (remote || typeof policy?.maxGpsAccuracyMeters !== "number" || usableAccuracy(submissionLocation.accuracy, policy.maxGpsAccuracyMeters))
  );
  const isLifecycleEnded = policy?.lifecycleStatus === "ENDED" || policy?.lifecycleStatus === "COMPLETED" || (Boolean(policy?.internshipEndDate) && Boolean(today?.attendanceDate) && policy!.internshipEndDate < today!.attendanceDate);
  const isLifecycleNotStarted = policy?.lifecycleStatus === "NOT_STARTED" || (Boolean(policy?.internshipStartDate) && Boolean(today?.attendanceDate) && policy!.internshipStartDate > today!.attendanceDate);
  const automatic = attendance ? isAutomaticAbsence(attendance) : false;
  const todaySubmissions: Attendance[] = (today?.todaySubmissions && today.todaySubmissions.length > 0)
    ? today.todaySubmissions
    : (attendance && !automatic ? [attendance] : []);
  const submissionsCount = today?.submissionsCount ?? todaySubmissions.length;
  const maxDailySubmissions = today?.maxDailySubmissions ?? 2;
  const submitted = submissionsCount >= maxDailySubmissions;
  const hasAnySubmission = todaySubmissions.length > 0;
  const locationCaptureControl = <div className="mt-4 text-sm text-slate-600"><button data-button-variant="text" type="button" disabled={submitting || locating} onClick={includeSubmissionLocation} className="hover:underline">{locating ? "Capturing submission location..." : poorAccuracy ? "Retry GPS capture" : "Capture submission location (required)"}</button><p className="mt-1">Location is required to submit attendance.{!remote && typeof policy?.maxGpsAccuracyMeters === "number" && ` Required GPS accuracy: ${policy.maxGpsAccuracyMeters} m or better.`}</p>{locationNotice && <p role={poorAccuracy ? "alert" : "status"} className={`mt-2 ${poorAccuracy ? "text-amber-800" : ""}`}>{locationNotice}</p>}</div>;
  return (
    <section className="mt-5 rounded-2xl border border-slate-200 bg-white p-7 lg:col-span-2">
      {internships.length>1&&<label className="mb-5 block text-sm font-semibold">Attendance internship<select className="input mt-2" value={internshipId??""} onChange={e=>{stopCamera();setFlowOpen(false);setError("");setAiError("");setSubmissionLocation(null);setLocationNotice("");setLocating(false);setInternshipId(Number(e.target.value));setToday(null);setPolicy(null);setLoading(true)}}>{internships.map(i=><option key={i.id} value={i.id}>Internship {i.internshipNumber} — {i.companyName}</option>)}</select></label>}
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.18em] text-blue-700">Today&apos;s Attendance</p>
          <h2 className="mt-3 text-xl font-bold">
            {loading ? "Checking attendance…" : attendance ? attendanceLabel(attendance) : isLifecycleEnded ? "Internship Completed" : isLifecycleNotStarted ? "Internship Not Started" : "Not submitted"}
          </h2>
          {!loading && !isLifecycleEnded && !isLifecycleNotStarted && (
            <p className="mt-1 text-xs font-semibold text-slate-500">
              Daily submissions: {submissionsCount} / {maxDailySubmissions} recorded today
            </p>
          )}
        </div>
        {attendance && attendance.attendanceResult === "PRESENT" && <span className={`rounded-full px-3 py-1.5 text-xs font-semibold ${attendance.livenessStatus === "VERIFIED" ? "bg-emerald-50 text-emerald-800" : "bg-amber-50 text-amber-800"}`}>{livenessLabel(attendance.livenessStatus)}</span>}
      </div>
      {!loading && attendance && !hasAnySubmission ? <p className="mt-3 text-sm text-slate-600">{automatic ? "Automatically marked absent for the missed working day." : null}</p> : !loading && hasAnySubmission ? (
        <div className="mt-3 space-y-2">
          <p className="text-sm text-slate-600">
            Submitted at {new Date(attendance!.serverSubmittedAt).toLocaleTimeString([], { hour: "numeric", minute: "2-digit", timeZone: today?.attendanceZone })} using server time.
          </p>
          {todaySubmissions.length > 0 && (
            <div className="rounded-xl border border-slate-200 bg-slate-50 p-3 text-xs space-y-1.5">
              {todaySubmissions.map((entry, idx) => (
                <div key={entry.id ?? idx} className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-semibold text-slate-800">
                    Submission #{entry.submissionSlot ?? idx + 1} · {attendanceLabel(entry)}
                  </span>
                  <span className="text-slate-600">
                    {new Date(entry.serverSubmittedAt).toLocaleTimeString([], { hour: "numeric", minute: "2-digit", second: "2-digit", timeZone: today?.attendanceZone })}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      ) : !loading && isLifecycleEnded ? <p className="mt-3 text-sm text-slate-600">This internship concluded on {policy?.internshipEndDate}. Attendance is no longer active.</p> : !loading && isLifecycleNotStarted ? <p className="mt-3 text-sm text-slate-600">Attendance tracking starts on {policy?.internshipStartDate}.</p> : !loading && <p className="mt-3 text-sm text-slate-600">Submit up to 2 attendance records at any time during a working day in {today?.attendanceZone ?? "Asia/Kolkata"}. Every submission records your timestamp automatically.</p>}
      {!loading && attendance && !automatic && <AttendanceLocations evidence={attendance.evidence} />}
      {!loading && attendance && !automatic && <LocationVerificationResult evidence={attendance.evidence} maxGpsAccuracyMeters={policy?.maxGpsAccuracyMeters}/>}
      {!loading && attendance?.attendanceResult === "PENDING_MENTOR_REVIEW" && <p role="alert" className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-900">Your evidence was saved and is pending mentor review. {attendance.evidence?.locationVerificationStatus && attendance.evidence.locationVerificationStatus !== "VERIFIED" && attendance.evidence.locationVerificationStatus !== "NOT_APPLICABLE" ? `Location issue: ${attendance.evidence.locationVerificationStatus.replaceAll("_", " ").toLowerCase()}.` : attendance.livenessStatus !== "VERIFIED" ? `Verification issue: ${attendance.evidence?.verificationReason?.replaceAll("_", " ").toLowerCase() || livenessLabel(attendance.livenessStatus)}.` : "A mandatory verification check requires review."}</p>}
      {!loading && attendance?.acknowledgement && <p className="mt-3 text-sm text-slate-600">Viewed by mentor · {new Date(attendance.acknowledgement.seenAt).toLocaleString("en-IN", { timeZone: today?.attendanceZone })}</p>}
      {!loading && policy?.todayWorkMode && <p className="mt-3 text-sm font-semibold text-slate-700">Today&apos;s expected work mode: {policy.todayWorkMode === "OFFICE" ? "Office" : "Remote"}</p>}
      {!loading && policy?.internshipMode === "HYBRID" && schedule && <div className="mt-4 flex flex-wrap gap-2">{schedule.entries.map(entry => <span key={entry.dayOfWeek} className="rounded-lg bg-slate-100 px-2.5 py-1.5 text-xs text-slate-700">{entry.dayOfWeek.slice(0, 3)} · {entry.workMode === "OFFICE" ? "Office" : "Remote"}</span>)}</div>}

      {/* Approved Company Holiday Notice */}
      {!loading && policy?.approvedHoliday && (
        <div className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900">
          <p className="font-bold">Company Holiday (Approved)</p>
          <p className="mt-1">Today is an approved company holiday. You are excused from submitting attendance today.</p>
        </div>
      )}

      {/* Pending Company Holiday Notice */}
      {!loading && policy?.pendingHoliday && (
        <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
          <p className="font-bold">Company Holiday Request Pending</p>
          <p className="mt-1">Your company holiday request has been submitted and is awaiting faculty mentor verification.</p>
        </div>
      )}

      {/* Holiday Request Success Notice */}
      {holidaySuccess && (
        <p role="status" className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-900">
          {holidaySuccess}
        </p>
      )}

      {/* Scheduled Day Off Banner */}
      {!loading && !submitted && !isLifecycleEnded && !isLifecycleNotStarted && policy?.scheduledDayOff && !policy.approvedHoliday && !forceExtraWork && !policy.approvedExtraWorkday && (
        <div className="mt-5 rounded-2xl border border-blue-200 bg-blue-50/60 p-5">
          <h3 className="text-base font-bold text-blue-950">Today is your scheduled day off</h3>
          <p className="mt-1 text-sm text-blue-800">
            According to your company schedule, today is not a regular working day. No attendance is required, and no automatic absence will be recorded.
          </p>
          <div className="mt-4 flex flex-wrap gap-3">
            <button
              type="button"
              onClick={() => setForceExtraWork(true)}
              className="rounded-xl border border-blue-700 bg-white px-4 py-2 text-sm font-semibold text-blue-800 shadow-sm hover:bg-blue-50"
            >
              Working today (extra work)
            </button>
          </div>
        </div>
      )}

      {/* Extra Workday Confirmed Indicator */}
      {!loading && (forceExtraWork || policy?.approvedExtraWorkday) && !submitted && !isLifecycleEnded && !isLifecycleNotStarted && (
        <div className="mt-4 rounded-xl border border-blue-300 bg-blue-50 p-3 text-sm text-blue-900">
          <p className="font-semibold">Extra Workday Mode Active</p>
          <p className="text-xs text-blue-800">You are submitting attendance for an extra workday outside your normal schedule. Valid attendance will be verified and counted.</p>
        </div>
      )}

      {/* Report Company Holiday Option on working days */}
      {!loading && !submitted && !isLifecycleEnded && !isLifecycleNotStarted && !windowClosed && !policy?.approvedHoliday && !policy?.pendingHoliday && !holidayModalOpen && (
        <div className="mt-4">
          <button
            type="button"
            data-button-variant="outline"
            onClick={() => { setHolidayModalOpen(true); setHolidayError(""); }}
            className="text-xs text-slate-600 hover:text-slate-900"
          >
            Report Company Holiday for Today
          </button>
        </div>
      )}

      {/* Holiday Reporting Form */}
      {holidayModalOpen && (
        <div className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
          <h3 className="text-sm font-bold text-slate-900">Report Exceptional Company Holiday</h3>
          <p className="mt-1 text-xs text-slate-600">
            Report a company-specific holiday for {today?.attendanceDate}. Your faculty mentor will review and approve this exception.
          </p>
          <div className="mt-3">
            <textarea
              rows={3}
              placeholder="Reason for company holiday (e.g. Annual Company Day, Facility Maintenance)"
              value={holidayReason}
              onChange={e => setHolidayReason(e.target.value)}
              className="w-full rounded-xl border border-slate-300 bg-white p-3 text-sm text-slate-900 focus:border-blue-600 focus:outline-none"
            />
          </div>
          {holidayError && <p role="alert" className="mt-2 text-xs text-red-700">{holidayError}</p>}
          <div className="mt-3 flex gap-2">
            <button
              type="button"
              disabled={holidaySubmitting || !holidayReason.trim()}
              onClick={submitHoliday}
              className="text-xs"
            >
              {holidaySubmitting ? "Submitting…" : "Submit Holiday Request"}
            </button>
            <button
              type="button"
              data-button-variant="outline"
              onClick={() => { setHolidayModalOpen(false); setHolidayReason(""); setHolidayError(""); }}
              className="text-xs"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {!loading && !submitted && !policy && <p role="alert" className="mt-5 rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-800">Today&apos;s attendance policy could not be confirmed. Reload the dashboard before submitting attendance.</p>}
      {!loading && !submitted && policy && !policy.configurationReady && <p className="mt-5 rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-800">Today&apos;s work mode has not been configured. Attendance can be recorded; liveness will be marked as not performed.</p>}
      {!loading && !submitted && !isLifecycleEnded && !isLifecycleNotStarted && policy && !flowOpen && (!policy.scheduledDayOff || forceExtraWork || policy.approvedExtraWorkday) && !policy.approvedHoliday && <button type="button" disabled={windowClosed || !attendanceSubmissionOpen(today) || submitting || ((!physical && !remote) && (!submissionLocation || locating))} onClick={() => { if (physical || remote) void startCamera(); else void submitBasic(); }} className="mt-6 text-sm">{submitting ? "Submitting…" : submissionsCount > 0 ? `Mark Today's Attendance (${submissionsCount + 1} of ${maxDailySubmissions})` : "Mark Today's Attendance"}</button>}
      {!loading && !submitted && !isLifecycleEnded && !isLifecycleNotStarted && policy && !physical && !remote && (!policy.scheduledDayOff || forceExtraWork || policy.approvedExtraWorkday) && !policy.approvedHoliday && locationCaptureControl}
      {!loading && !submitted && !windowClosed && flowOpen && <div className="mt-6 rounded-xl border border-slate-200 bg-slate-50 p-4">
        {/* Mode Selector Tabs: Take Photo vs Upload Photo */}
        <div className="mb-5 flex border-b border-slate-200 pb-3 gap-3">
          <button
            type="button"
            onClick={() => { setAttendanceMode("camera"); if (!photo) void startCamera(); }}
            className={`px-4 py-2 text-sm font-semibold rounded-lg transition-colors ${attendanceMode === "camera" ? "bg-blue-600 text-white" : "bg-slate-200 text-slate-700 hover:bg-slate-300"}`}
          >
            Take Photo (Camera + Edge AI)
          </button>
          <button
            type="button"
            onClick={() => { setAttendanceMode("upload"); stopCamera(); }}
            className={`px-4 py-2 text-sm font-semibold rounded-lg transition-colors ${attendanceMode === "upload" ? "bg-blue-600 text-white" : "bg-slate-200 text-slate-700 hover:bg-slate-300"}`}
          >
            Upload Photo (Metadata Verification)
          </button>
        </div>

        {attendanceMode === "upload" ? (
          <div className="space-y-4">
            <div className="rounded-xl bg-blue-50 p-4 text-sm text-blue-950">
              <p className="font-bold">Photo Upload & Metadata Verification</p>
              <p className="mt-1">Select an original photo taken today at your approved workplace. Supported formats: JPEG, PNG, HEIC/HEIF.</p>
              <p className="mt-1 text-xs text-blue-800">The server verifies the photo&apos;s original capture date, time, and GPS coordinates against your workplace geofence. The image file is not stored.</p>
            </div>

            <label className="block">
              <span className="block text-sm font-semibold text-slate-700 mb-2">Select Workplace Photograph</span>
              <input
                type="file"
                accept="image/jpeg,image/png,image/heic,image/heif,.jpg,.jpeg,.png,.heic,.heif"
                disabled={submitting}
                onChange={e => {
                  const file = e.target.files?.[0] || null;
                  setUploadedPhotoFile(file);
                  if (file) {
                    const url = URL.createObjectURL(file);
                    setUploadedPhotoPreview(url);
                  } else {
                    setUploadedPhotoPreview("");
                  }
                  setError("");
                }}
                className="block w-full text-sm text-slate-500 file:mr-4 file:py-2 file:px-4 file:rounded-xl file:border-0 file:text-sm file:font-semibold file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100 cursor-pointer"
              />
            </label>

            {uploadedPhotoPreview && (
              <div className="mt-3">
                <p className="text-xs font-medium text-slate-500 mb-1">Selected Photo Preview:</p>
                <img src={uploadedPhotoPreview} alt="Selected workplace photo" className="aspect-video w-full max-w-md rounded-xl bg-slate-950 object-cover border border-slate-200" />
              </div>
            )}

            <div className="mt-5 flex flex-wrap items-center gap-3">
              <button
                type="button"
                disabled={submitting || !uploadedPhotoFile || windowClosed || !attendanceSubmissionOpen(today)}
                onClick={() => void submitUploadedPhoto()}
                className="text-sm"
              >
                {submitting ? <LoadingSpinner label="Verifying & Submitting…" /> : "Submit Uploaded Photo"}
              </button>
              <button
                data-button-variant="text"
                type="button"
                disabled={submitting}
                onClick={() => {
                  setUploadedPhotoFile(null);
                  setUploadedPhotoPreview("");
                  setFlowOpen(false);
                  setError("");
                }}
                className="text-sm"
              >
                Cancel
              </button>
            </div>
          </div>
        ) : (
          <div>
            <div className="mb-4 rounded-xl bg-blue-50 p-4 text-sm text-blue-950"><p className="font-bold">Edge Verification</p><p className="mt-1">Camera analysis verifies a live attendance capture on this device. Face recognition and identity matching are not performed.</p></div>
            {!photo ? <video ref={videoRef} playsInline muted style={{ transform: isFrontCamera ? "scaleX(-1)" : "none" }} className="aspect-video w-full max-w-xl rounded-xl bg-slate-950 object-cover" /> : <img src={photoUrl} alt="Fresh attendance camera capture" className="aspect-video w-full max-w-xl rounded-xl bg-slate-950 object-cover" />}
            <canvas ref={canvasRef} className="hidden" />
            <div className="mt-4 flex flex-wrap items-center gap-3 text-sm">
              <span className={cameraStatus === "Camera ready" ? "font-semibold text-emerald-700" : cameraStatus.includes("denied") || cameraStatus.includes("in use") || cameraStatus.includes("No camera") || cameraStatus.includes("failed") ? "font-semibold text-red-700" : "font-medium text-slate-600"}>{cameraStatus || "Camera loading…"}</span>
            </div>
            {!photo && aiSession && <div className="mt-4 max-w-xl rounded-xl border border-slate-200 bg-white p-4 text-sm"><p className="font-semibold">Liveness challenge: {challengeLabel(aiSession.challenge)}</p><div className="mt-3 grid gap-2 sm:grid-cols-3"><span>{aiState?.faceDetected ? "✓" : "○"} Face detected</span><span>{aiState?.singleFace && aiState.qualityPassed ? "✓" : "○"} Face positioned</span><span>{aiState?.livenessPassed ? "✓" : "○"} Liveness confirmed</span></div><p className="mt-3 text-slate-600">{aiState?.guidance ?? "Loading face analysis…"}</p></div>}
            {aiError && <p role="alert" className="mt-4 rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-800">{aiError}</p>}
            {!loading && !submitted && policy && photo && (
              <div className="mt-4 text-sm text-slate-600">
                {hasValidLocation ? (
                  <>
                    <p role="status" className="font-semibold text-emerald-700">Location captured successfully</p>
                    {locationNotice && <p className="mt-1 text-slate-600">{locationNotice}</p>}
                  </>
                ) : (
                  <>
                    <p className="mt-1">Location is required to submit attendance.{!remote && typeof policy?.maxGpsAccuracyMeters === "number" && ` Required GPS accuracy: ${policy.maxGpsAccuracyMeters} m or better.`}</p>
                    {locationNotice && <p role={poorAccuracy ? "alert" : "status"} className={`mt-2 ${poorAccuracy ? "text-amber-800" : "font-medium text-red-700"}`}>{locationNotice}</p>}
                  </>
                )}
              </div>
            )}
            {photo && remote && (
              <div className="mt-4 w-full max-w-xl">
                <label htmlFor="daily-work-summary" className="block text-sm font-semibold text-slate-700">
                  Today&apos;s work/activity
                </label>
                <textarea
                  id="daily-work-summary"
                  value={dailyWorkSummary}
                  onChange={event => setDailyWorkSummary(event.target.value)}
                  minLength={20}
                  maxLength={1000}
                  rows={4}
                  placeholder="Describe the work you completed today (20–1000 characters)."
                  className="mt-2 w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm font-normal text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-100"
                />
                <div className="mt-1 flex justify-between text-xs text-slate-500">
                  <span>{dailyWorkSummary.trim().length < 20 ? `Minimum 20 characters required (${20 - dailyWorkSummary.trim().length} more needed)` : "Minimum character requirement met"}</span>
                  <span>{dailyWorkSummary.trim().length} / 1000 characters</span>
                </div>
              </div>
            )}
            <div className="mt-4 flex flex-wrap items-center gap-3">
              {photo ? (
                !hasValidLocation ? (
                  <>
                    <button
                      type="button"
                      disabled={submitting || locating}
                      onClick={includeSubmissionLocation}
                      className="text-sm"
                    >
                      {locating ? <LoadingSpinner label="Capturing location…" /> : poorAccuracy ? "Retry GPS capture" : "Capture Location"}
                    </button>
                  </>
                ) : (
                  <>
                    <button
                      type="button"
                      disabled={
                        windowClosed ||
                        !attendanceSubmissionOpen(today) ||
                        submitting ||
                        locating ||
                        !submissionLocation ||
                        (remote && dailyWorkSummary.trim().length < 20)
                      }
                      onClick={() => void (remote ? submitRemote() : submitPhysical())}
                      className="text-sm"
                    >
                      {submitting ? <LoadingSpinner label="Submitting…" /> : "Submit Attendance"}
                    </button>
                    <button
                      data-button-variant="outline"
                      type="button"
                      disabled={submitting}
                      onClick={() => void startCamera(true)}
                      className="text-sm"
                    >
                      Retake Photo
                    </button>
                  </>
                )
              ) : (
                <>
                  {streamRef.current && (
                    <button
                      type="button"
                      disabled={!aiState?.livenessPassed || !aiState.qualityPassed}
                      onClick={() => void capture()}
                      className="text-sm"
                    >
                      Capture Attendance
                    </button>
                  )}
                  {(cameraStatus.includes("denied") ||
                    cameraStatus.includes("in use") ||
                    cameraStatus.includes("No camera") ||
                    cameraStatus.includes("failed") ||
                    Boolean(aiError)) && (
                    <button
                      data-button-variant="outline"
                      type="button"
                      onClick={() => void startCamera()}
                      className="text-sm"
                    >
                      Retry Camera
                    </button>
                  )}
                </>
              )}
              <button
                data-button-variant="text"
                type="button"
                disabled={submitting}
                onClick={() => {
                  stopCamera();
                  setLocating(false);
                  setSubmissionLocation(null);
                  setLocationNotice("");
                  setFlowOpen(false);
                  setError("");
                }}
                className="text-sm"
              >
                Cancel
              </button>
            </div>
          </div>
        )}      </div>}
      {error && <p role="alert" className="mt-4 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}
    </section>
  );
}
