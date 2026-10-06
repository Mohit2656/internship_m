import { MonthlyProgressReport } from "@/lib/monthly-progress";

export function ProgressPhotoVerification({ report }: { report: MonthlyProgressReport }) {
  const verified = report.geoVerificationStatus === "VERIFIED";
  return <section aria-label="EXIF/GPS verification" className={`mt-4 rounded-xl p-4 text-sm ${verified ? "bg-emerald-50 text-emerald-800" : "bg-amber-50 text-amber-800"}`}>
    <h3 className="font-semibold">EXIF/GPS verification: {verified ? "GPS metadata verified" : report.geoVerificationStatus === null ? "Not assessed" : "Geo-tag not verified"}</h3>
    <p className="mt-1">{report.geoVerificationMessage ?? "This photograph predates metadata verification."}</p>
    {report.captureTimeStatus === "MISSING_CAPTURE_TIME" && <p className="mt-1">No EXIF capture timestamp was found.</p>}
    {report.captureTimeStatus === "INVALID_METADATA" && <p className="mt-1">The EXIF capture timestamp could not be parsed.</p>}
    {(report.cameraMake || report.cameraModel) && <p className="mt-1">Camera: {[report.cameraMake, report.cameraModel].filter(Boolean).join(" ")}</p>}
    <p className="mt-2 text-xs">EXIF can be edited. Valid metadata does not prove physical presence. SHA-256 identifies the uploaded file.</p>
  </section>;
}
