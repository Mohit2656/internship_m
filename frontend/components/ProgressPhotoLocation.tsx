import type { MonthlyProgressReport } from "@/lib/monthly-progress";

type PhotoLocation = Pick<MonthlyProgressReport, "exifLatitude" | "exifLongitude" | "geoVerificationStatus">;

export function ProgressPhotoLocation({ report }: { report: PhotoLocation }) {
  const latitude = report.exifLatitude;
  const longitude = report.exifLongitude;
  const available = typeof latitude === "number" && Number.isFinite(latitude) && Math.abs(latitude) <= 90
    && typeof longitude === "number" && Number.isFinite(longitude) && Math.abs(longitude) <= 180;
  return <section aria-label="Photo capture location" className="mt-4 rounded-xl bg-slate-50 p-4 text-sm">
    <h3 className="font-semibold text-slate-900">Photo capture location</h3>
    {available ? <>
      <dl className="mt-2 grid gap-2 sm:grid-cols-2">
        <div><dt className="text-slate-500">Latitude</dt><dd className="font-semibold">{latitude}</dd></div>
        <div><dt className="text-slate-500">Longitude</dt><dd className="font-semibold">{longitude}</dd></div>
      </dl>
      <a href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${latitude},${longitude}`)}`} target="_blank" rel="noopener noreferrer" className="mt-3 inline-block font-semibold text-blue-700 hover:underline">View photo coordinates on Google Maps</a>
      <p className="mt-2 text-xs text-slate-500">Coordinates come from image metadata and do not prove physical presence.</p>
    </> : <p className="mt-2 text-slate-600">{report.geoVerificationStatus === "INVALID_GPS" ? "Photo location unavailable: the image GPS coordinates are invalid." : report.geoVerificationStatus === "MISSING_GPS" ? "Photo location unavailable: no GPS coordinates were found in the image." : "Photo location unavailable: no usable GPS coordinates are recorded for this photograph."}</p>}
  </section>;
}
