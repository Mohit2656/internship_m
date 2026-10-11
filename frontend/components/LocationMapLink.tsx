export function LocationMapLink({ latitude, longitude, label }: { latitude: unknown; longitude: unknown; label: string }) {
  if (typeof latitude !== "number" || typeof longitude !== "number" || !Number.isFinite(latitude) || !Number.isFinite(longitude) || Math.abs(latitude)>90 || Math.abs(longitude)>180) return null;
  return <a className="mt-2 inline-block text-sm font-semibold text-blue-700 hover:underline" href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${latitude},${longitude}`)}`} target="_blank" rel="noopener noreferrer">{label}</a>;
}
