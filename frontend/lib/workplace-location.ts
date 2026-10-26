export type WorkplaceCoordinates = { latitude: number; longitude: number };

export function validWorkplaceCoordinates(value: WorkplaceCoordinates | null | undefined): value is WorkplaceCoordinates {
  return Boolean(value && Number.isFinite(value.latitude) && Number.isFinite(value.longitude)
    && Math.abs(value.latitude) <= 90 && Math.abs(value.longitude) <= 180);
}

export function workplaceGeolocationError(error: Pick<GeolocationPositionError, "code">): string {
  if (error.code === 1) return "Location permission was denied. Allow location access in your browser settings, then retry, or select your workplace on the map.";
  if (error.code === 3) return "Finding your location timed out. Move somewhere with a clearer GPS signal and retry, or select your workplace on the map.";
  return "Your device location is unavailable. Retry, or select your workplace on the map.";
}

export function confirmedWorkplaceMatches(value: (WorkplaceCoordinates & { address: string }) | null, draft: WorkplaceCoordinates | null, address: string): boolean {
  return Boolean(validWorkplaceCoordinates(value) && validWorkplaceCoordinates(draft)
    && value.latitude === draft.latitude && value.longitude === draft.longitude && value.address.trim() === address.trim());
}
