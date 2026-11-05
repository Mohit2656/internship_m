"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { AxiosError } from "axios";
import { api, ApiError } from "@/lib/api";
import { AiVerificationEvidence, AiVerificationSession, Attendance, AttendancePolicy, HybridSchedule, TodayAttendance, attendanceLabel, attendanceSubmissionOpen, isAutomaticAbsence, livenessLabel } from "@/lib/attendance";
import { challengeLabel, EdgeAiAnalyzer, EdgeAiFrameState, EDGE_AI_MODEL_VERSION } from "@/lib/edge-ai";
import { AttendanceLocations, validSubmissionCoordinates } from "@/components/AttendanceLocations";
import { LocationVerificationResult } from "@/components/LocationVerificationResult";
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
    if (today.latePermissionEnabled) {
      if (typeof window === "undefined") return;
      const timer = window.setInterval(() => {
        if (!attendanceSubmissionOpen(today)) { setWindowClosed(true); stopCamera(); setLocating(false); setFlowOpen(false); }
      }, 1000);
      return () => window.clearInterval(timer);
    }
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
    const isSubmitted = Boolean(today?.attendance && !isAutomaticAbsence(today.attendance));
    if (isSubmitted && flowOpen) {
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

  async function startCamera() {
    const isSubmitted = Boolean(today?.attendance && !isAutomaticAbsence(today.attendance));
    if (isSubmitted) {
      stopCamera();
      setFlowOpen(false);
      setError("Today's attendance has already been submitted for this internship.");
      return;
    }
    if (!attendanceSubmissionOpen(today)) {
      setWindowClosed(true);
      setError("Today's attendance submission window has closed.");
      return;
    }
    locationRequestRef.current += 1; setSubmissionLocation(null); setLocationNotice(""); setLocating(false);
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

  async function submitBasic() {
    if (!attendanceSubmissionOpen(today)) { setWindowClosed(true); setError("Today's attendance submission window has closed."); return; }
    if (policy?.verificationMethod !== "BASIC" && policy?.verificationMethod !== "UNAVAILABLE") {
      setError("Use today's required attendance evidence flow.");
      return;
    }
    if (!submissionLocation || !validSubmissionCoordinates(submissionLocation.latitude, submissionLocation.longitude)) {
      setError("Capture your submission location before submitting attendance."); return;
    }
    setSubmitting(true); setError("");
    try {
      const { data } = await api.post<Attendance>("/student/attendance/today", null, {
        params: {
          ...(internshipId ? { internshipId } : {}),
          latitude: submissionLocation.latitude,
          longitude: submissionLocation.longitude,
          ...(Number.isFinite(submissionLocation.accuracy) && submissionLocation.accuracy >= 0 ? { accuracy: submissionLocation.accuracy } : {}),
          ...(submissionLocation.capturedAt ? { locationCapturedAt: submissionLocation.capturedAt } : {})
        }
      });
      statusVersionRef.current += 1;
      setToday(current => ({ ...current, attendanceDate: data.attendanceDate, attendance: data, submissionOpen: false }));
      stopCamera(); setFlowOpen(false);
    } catch (cause) {
      setError(parseAttendanceError(cause));
    } finally { setSubmitting(false); }
  }

  async function submitPhysical() {
    if (!attendanceSubmissionOpen(today)) { setWindowClosed(true); setError("Today's attendance submission window has closed."); return; }
    if (!photo) return;
    if (!submissionLocation || !validSubmissionCoordinates(submissionLocation.latitude, submissionLocation.longitude)) {
      setError("Capture your submission location before submitting attendance."); return;
    }
    setSubmitting(true); setError("");
    const form = new FormData();
    form.append("photo", photo, "attendance-capture.jpg");
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
          } : {})
        },
      });
      statusVersionRef.current += 1;
      setToday(current => ({ ...current, attendanceDate: data.attendanceDate, attendance: data, submissionOpen: false }));
      stopCamera();
      setFlowOpen(false);
    } catch (cause) {
      setError(parseAttendanceError(cause));
    } finally { setSubmitting(false); }
  }

  async function submitRemote() {
    if (!attendanceSubmissionOpen(today)) { setWindowClosed(true); setError("Today's attendance submission window has closed."); return; }
    if (!photo) return;
    const summary = dailyWorkSummary.trim();
    if (summary.length < 20 || summary.length > 1000) {
      setError("Daily work summary must contain between 20 and 1000 characters."); return;
    }
    if (!submissionLocation || !validSubmissionCoordinates(submissionLocation.latitude, submissionLocation.longitude)) {
      setError("Capture your submission location before submitting attendance."); return;
    }
    setSubmitting(true); setError("");
    const form = new FormData();
    form.append("photo", photo, "attendance-capture.jpg");
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
          } : {})
        },
      });
      statusVersionRef.current += 1;
      setToday(current => ({ ...current, attendanceDate: data.attendanceDate, attendance: data, submissionOpen: false }));
      stopCamera();
      setFlowOpen(false);
    } catch (cause) {
      setError(parseAttendanceError(cause));
    } finally { setSubmitting(false); }
  }

  const attendance = today?.attendance;
  const physical = policy?.verificationMethod === "PHYSICAL";
  const remote = policy?.verificationMethod === "REMOTE";
  const poorAccuracy = Boolean(submissionLocation && !remote && typeof policy?.maxGpsAccuracyMeters === "number" && !usableAccuracy(submissionLocation.accuracy, policy.maxGpsAccuracyMeters));
  const automatic = attendance ? isAutomaticAbsence(attendance) : false;
  const submitted = Boolean(attendance && !automatic);
  const latePermissionActive = Boolean(today?.latePermissionEnabled && attendanceSubmissionOpen(today));
  const deadline = today?.submissionDeadlineAt ? new Intl.DateTimeFormat("en-IN", { dateStyle: "medium", timeStyle: "short", timeZone: today.attendanceZone }).format(new Date(today.submissionDeadlineAt)) : null;
  const locationCaptureControl = <div className="mt-4 text-sm text-slate-600"><button data-button-variant="text" type="button" disabled={submitting || locating} onClick={includeSubmissionLocation} className="hover:underline">{locating ? "Capturing submission location..." : poorAccuracy ? "Retry GPS capture" : "Capture submission location (required)"}</button><p className="mt-1">Location is required to submit attendance.{!remote && typeof policy?.maxGpsAccuracyMeters === "number" && ` Required GPS accuracy: ${policy.maxGpsAccuracyMeters} m or better.`}</p>{locationNotice && <p role={poorAccuracy ? "alert" : "status"} className={`mt-2 ${poorAccuracy ? "text-amber-800" : ""}`}>{locationNotice}</p>}</div>;
  return (
    <section className="mt-5 rounded-2xl border border-slate-200 bg-white p-7 lg:col-span-2">
      {internships.length>1&&<label className="mb-5 block text-sm font-semibold">Attendance internship<select className="input mt-2" value={internshipId??""} onChange={e=>{stopCamera();setFlowOpen(false);setError("");setAiError("");setInternshipId(Number(e.target.value));setToday(null);setPolicy(null);setLoading(true)}}>{internships.map(i=><option key={i.id} value={i.id}>Internship {i.internshipNumber} — {i.companyName}</option>)}</select></label>}
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div><p className="text-xs font-bold uppercase tracking-[0.18em] text-blue-700">Today&apos;s Attendance</p><h2 className="mt-3 text-xl font-bold">{loading ? "Checking attendance…" : attendance ? attendanceLabel(attendance) : "Not submitted"}</h2></div>
        {attendance && attendance.attendanceResult === "PRESENT" && <span className={`rounded-full px-3 py-1.5 text-xs font-semibold ${attendance.livenessStatus === "VERIFIED" ? "bg-emerald-50 text-emerald-800" : "bg-amber-50 text-amber-800"}`}>{livenessLabel(attendance.livenessStatus)}</span>}
      </div>
      {!loading && attendance ? <p className="mt-3 text-sm text-slate-600">{automatic ? "Automatically marked absent for the full working day because attendance was not submitted before the deadline." : <>Submitted at {new Date(attendance.serverSubmittedAt).toLocaleTimeString([], { hour: "numeric", minute: "2-digit", timeZone: today?.attendanceZone })} using server time.</>}</p> : !loading && <p className="mt-3 text-sm text-slate-600">Attendance can be submitted once per working day during your registered internship period.{deadline && <> Submit before {deadline} ({today?.attendanceZone}). Missing attendance is marked absent for the full working day after this deadline.</>}</p>}
      {!loading && latePermissionActive && <p role="status" className="mt-4 rounded-lg bg-blue-50 px-4 py-3 text-sm text-blue-800">Your faculty mentor has enabled late attendance submission for {today?.attendanceDate} ({today?.attendanceZone}). Permission expires at the end of this day. Complete the required attendance validation to submit.{automatic && " A valid submission will update today's automatic absence."}</p>}
      {!loading && attendance?.submittedLate && <p className="mt-3 text-sm font-semibold text-blue-700">Submitted late with faculty mentor permission.</p>}
      {!loading && windowClosed && !submitted && <p role="status" className="mt-3 text-sm text-amber-800">Today's submission window has closed. Any missing attendance will be recorded as absent automatically.</p>}
      {!loading && attendance && !automatic && <AttendanceLocations evidence={attendance.evidence} />}
      {!loading && attendance && !automatic && <LocationVerificationResult evidence={attendance.evidence} maxGpsAccuracyMeters={policy?.maxGpsAccuracyMeters}/>}
      {!loading && attendance?.attendanceResult === "PENDING_MENTOR_REVIEW" && <p role="alert" className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-900">Your evidence was saved and is pending mentor review. {attendance.evidence?.locationVerificationStatus && attendance.evidence.locationVerificationStatus !== "VERIFIED" && attendance.evidence.locationVerificationStatus !== "NOT_APPLICABLE" ? `Location issue: ${attendance.evidence.locationVerificationStatus.replaceAll("_", " ").toLowerCase()}.` : attendance.livenessStatus !== "VERIFIED" ? `Verification issue: ${attendance.evidence?.verificationReason?.replaceAll("_", " ").toLowerCase() || livenessLabel(attendance.livenessStatus)}.` : "A mandatory verification check requires review."}</p>}
      {!loading && attendance?.acknowledgement && <p className="mt-3 text-sm text-slate-600">Viewed by mentor · {new Date(attendance.acknowledgement.seenAt).toLocaleString("en-IN", { timeZone: today?.attendanceZone })}</p>}
      {!loading && policy?.todayWorkMode && <p className="mt-3 text-sm font-semibold text-slate-700">Today&apos;s expected work mode: {policy.todayWorkMode === "OFFICE" ? "Office" : "Remote"}</p>}
      {!loading && policy?.internshipMode === "HYBRID" && schedule && <div className="mt-4 flex flex-wrap gap-2">{schedule.entries.map(entry => <span key={entry.dayOfWeek} className="rounded-lg bg-slate-100 px-2.5 py-1.5 text-xs text-slate-700">{entry.dayOfWeek.slice(0, 3)} · {entry.workMode === "OFFICE" ? "Office" : "Remote"}</span>)}</div>}
      {!loading && !submitted && !policy && <p role="alert" className="mt-5 rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-800">Today&apos;s attendance policy could not be confirmed. Reload the dashboard before submitting attendance.</p>}
      {!loading && !submitted && policy && !policy.configurationReady && <p className="mt-5 rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-800">Today&apos;s work mode has not been configured. Attendance can be recorded; liveness will be marked as not performed.</p>}
      {!loading && !submitted && policy && !flowOpen && <button type="button" disabled={windowClosed || !attendanceSubmissionOpen(today) || submitting || ((!physical && !remote) && (!submissionLocation || locating))} onClick={() => { if (physical || remote) void startCamera(); else void submitBasic(); }} className="mt-6 text-sm">{submitting ? "Submitting…" : "Mark Today's Attendance"}</button>}
      {!loading && !submitted && policy && !physical && !remote && locationCaptureControl}
      {!loading && !submitted && !windowClosed && flowOpen && <div className="mt-6 rounded-xl border border-slate-200 bg-slate-50 p-4">
        <div className="mb-4 rounded-xl bg-blue-50 p-4 text-sm text-blue-950"><p className="font-bold">Edge Verification</p><p className="mt-1">Camera analysis verifies a live attendance capture on this device. Face recognition and identity matching are not performed.</p></div>
        {!photo ? <video ref={videoRef} playsInline muted style={{ transform: isFrontCamera ? "scaleX(-1)" : "none" }} className="aspect-video w-full max-w-xl rounded-xl bg-slate-950 object-cover" /> : <img src={photoUrl} alt="Fresh attendance camera capture" className="aspect-video w-full max-w-xl rounded-xl bg-slate-950 object-cover" />}
        <canvas ref={canvasRef} className="hidden" />
        <div className="mt-4 flex flex-wrap items-center gap-3 text-sm">
          <span className={cameraStatus === "Camera ready" ? "font-semibold text-emerald-700" : cameraStatus.includes("denied") || cameraStatus.includes("in use") || cameraStatus.includes("No camera") || cameraStatus.includes("failed") ? "font-semibold text-red-700" : "font-medium text-slate-600"}>{cameraStatus || "Camera loading…"}</span>
        </div>
        {!photo && aiSession && <div className="mt-4 max-w-xl rounded-xl border border-slate-200 bg-white p-4 text-sm"><p className="font-semibold">Liveness challenge: {challengeLabel(aiSession.challenge)}</p><div className="mt-3 grid gap-2 sm:grid-cols-3"><span>{aiState?.faceDetected ? "✓" : "○"} Face detected</span><span>{aiState?.singleFace && aiState.qualityPassed ? "✓" : "○"} Face positioned</span><span>{aiState?.livenessPassed ? "✓" : "○"} Liveness confirmed</span></div><p className="mt-3 text-slate-600">{aiState?.guidance ?? "Loading face analysis…"}</p></div>}
        {aiError && <p role="alert" className="mt-4 rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-800">{aiError}</p>}
        {!loading && !submitted && policy && photo && locationCaptureControl}
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
                {submitting ? "Submitting…" : "Submit Attendance"}
              </button>
              <button
                data-button-variant="outline"
                type="button"
                disabled={submitting}
                onClick={() => void startCamera()}
                className="text-sm"
              >
                Retake
              </button>
            </>
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
              setFlowOpen(false);
              setError("");
            }}
            className="text-sm"
          >
            Cancel
          </button>
        </div>
      </div>}
      {error && <p role="alert" className="mt-4 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}
    </section>
  );
}
