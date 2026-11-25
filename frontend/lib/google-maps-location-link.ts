import { validWorkplaceCoordinates, type WorkplaceCoordinates } from "@/lib/workplace-location";

export type GoogleMapsLinkKind =
  | "EXPLICIT_COORDINATES"
  | "PLACE_COORDINATE_METADATA"
  | "VIEWPORT_CENTRE"
  | "TEXT_SEARCH"
  | "PLACE_ID_ONLY"
  | "DIRECTIONS"
  | "SHORT_LINK"
  | "INVALID";

export type GoogleMapsLinkResult = {
  kind: GoogleMapsLinkKind;
  message: string;
  referencedCoordinates?: WorkplaceCoordinates;
  automaticPersistenceAllowed: false;
};

const GOOGLE_HOSTS = new Set(["google.com", "www.google.com", "maps.google.com", "google.co.in", "www.google.co.in", "maps.google.co.in"]);
const SHORT_HOSTS = new Set(["maps.app.goo.gl", "goo.gl"]);
const NUMBER = "-?(?:\\d+(?:\\.\\d+)?|\\.\\d+)";

function coordinates(latitude: string, longitude: string): WorkplaceCoordinates | undefined {
  const value = { latitude: Number(latitude), longitude: Number(longitude) };
  return validWorkplaceCoordinates(value) ? value : undefined;
}

/** Classifies a pasted link without fetching it, scraping a page, or logging its contents. */
export function inspectGoogleMapsLink(raw: string): GoogleMapsLinkResult {
  let url: URL;
  try {
    url = new URL(raw.trim());
  } catch {
    return invalid("Enter a complete HTTPS Google Maps link.");
  }
  const host = url.hostname.toLowerCase().replace(/\.$/, "");
  if (url.protocol !== "https:" || (!GOOGLE_HOSTS.has(host) && !SHORT_HOSTS.has(host))) {
    return invalid("Only HTTPS links from Google Maps are accepted.");
  }
  if (SHORT_HOSTS.has(host)) {
    return result("SHORT_LINK", "Shortened Google Maps links cannot be resolved safely without fetching an external redirect. Enter the coordinates manually.");
  }

  let path: string;
  try {
    path = decodeURIComponent(url.pathname);
  } catch {
    return invalid("This Google Maps link contains invalid URL encoding.");
  }
  if (/\/(?:maps\/)?(?:dir|directions)(?:\/|$)/i.test(path) || /\/maps\/preview\/directions/i.test(path)) {
    return result("DIRECTIONS", "Directions links can contain several origins, destinations, or waypoints. Enter the workplace coordinates manually.");
  }

  const query = url.searchParams.get("query");
  if (query) {
    const match = query.trim().match(new RegExp(`^(${NUMBER})\\s*,\\s*(${NUMBER})$`));
    if (match) {
      const point = coordinates(match[1], match[2]);
      if (!point) return invalid("The coordinate pair in this Google Maps link is outside the valid latitude or longitude range.");
      return result("EXPLICIT_COORDINATES", "This link explicitly references one coordinate pair. Automatic use is disabled pending data-use approval; enter and preview the coordinates manually.", point);
    }
    if (url.searchParams.has("query_place_id")) {
      return result("PLACE_ID_ONLY", "This link identifies a Google place but does not provide an independently usable coordinate pair. Enter the coordinates manually.");
    }
    return result("TEXT_SEARCH", "This link contains a place-name or address search, not a precise coordinate. Enter the coordinates manually.");
  }
  if (url.searchParams.has("query_place_id")) {
    return result("PLACE_ID_ONLY", "This link contains a Google place ID but no coordinate pair. Enter the coordinates manually.");
  }

  const placeMetadata = path.match(new RegExp(`!3d(${NUMBER})!4d(${NUMBER})`, "i"));
  if (placeMetadata) {
    const point = coordinates(placeMetadata[1], placeMetadata[2]);
    return point
      ? result("PLACE_COORDINATE_METADATA", "Coordinate metadata was found in a place URL, but automatic extraction and persistence are disabled pending data-use approval. Enter the coordinates manually.", point)
      : invalid("The place-coordinate metadata is outside the valid latitude or longitude range.");
  }

  const viewport = path.match(new RegExp(`@(${NUMBER}),(${NUMBER})(?:,|$)`, "i"));
  if (viewport) {
    return result("VIEWPORT_CENTRE", "The @latitude,longitude values describe the map viewport and are not proof of the workplace position. Enter the workplace coordinates manually.");
  }
  if (/\/maps\/(?:place|search)(?:\/|$)/i.test(path)) {
    return result("TEXT_SEARCH", "This Google Maps link does not contain an unambiguous coordinate pair. Enter the workplace coordinates manually.");
  }
  return invalid("This Google Maps link format does not provide a supported, unambiguous workplace location.");
}

function result(kind: Exclude<GoogleMapsLinkKind, "INVALID">, message: string, referencedCoordinates?: WorkplaceCoordinates): GoogleMapsLinkResult {
  return { kind, message, referencedCoordinates, automaticPersistenceAllowed: false };
}

function invalid(message: string): GoogleMapsLinkResult {
  return { kind: "INVALID", message, automaticPersistenceAllowed: false };
}

export type ManualCoordinateResult =
  | { ok: true; coordinates: WorkplaceCoordinates }
  | { ok: false; latitudeError?: string; longitudeError?: string; formError?: string };

export function parseManualCoordinates(latitudeText: string, longitudeText: string): ManualCoordinateResult {
  const latitudeValue = latitudeText.trim(), longitudeValue = longitudeText.trim();
  if (!latitudeValue || !longitudeValue) {
    return { ok: false, latitudeError: latitudeValue ? undefined : "Latitude is required.", longitudeError: longitudeValue ? undefined : "Longitude is required." };
  }
  const latitude = Number(latitudeValue), longitude = Number(longitudeValue);
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    return { ok: false, latitudeError: Number.isFinite(latitude) ? undefined : "Latitude must be a finite number.", longitudeError: Number.isFinite(longitude) ? undefined : "Longitude must be a finite number." };
  }
  if (Math.abs(latitude) > 90 && Math.abs(longitude) <= 90 && Math.abs(latitude) <= 180) {
    return { ok: false, formError: "Latitude and longitude appear to be reversed. Latitude must be between -90 and 90." };
  }
  const latitudeError = Math.abs(latitude) > 90 ? "Latitude must be between -90 and 90." : undefined;
  const longitudeError = Math.abs(longitude) > 180 ? "Longitude must be between -180 and 180." : undefined;
  if (latitudeError || longitudeError) return { ok: false, latitudeError, longitudeError };
  return { ok: true, coordinates: { latitude, longitude } };
}
