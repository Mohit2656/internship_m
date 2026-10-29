> Current workflow: see [DUAL_WORKPLACE_APPROVAL.md](DUAL_WORKPLACE_APPROVAL.md). V27 replaces the earlier single-confirmation behavior with separate assigned-mentor and IIIC approval. The content below records the earlier implementation and audit.

# Google Places workplace selection

The existing student internship form includes a workplace search. Selecting a place fills the company address and saves its Google place ID, formatted address, latitude and longitude in the existing internship_locations record for the authenticated student's internship. Backend validation rejects invalid coordinates. Selection is PENDING, not automatically verified: the existing IIIC location confirmation endpoint remains authoritative. Students cannot overwrite confirmed locations. Existing manual address entry remains available when Google is unavailable.

## Local configuration

Add the following to frontend/.env.local using your own restricted browser key:

```
NEXT_PUBLIC_GOOGLE_MAPS_API_KEY=your_browser_api_key
```

Enable Maps JavaScript API and Places API (New) for the Google Cloud project. Restrict the key to your application's HTTP referrers, including http://localhost:3000/* for local development, and to these APIs. Restart the frontend after changing the environment variable; production needs a rebuild. Browser keys are visible in the browser and must be restricted. No key has been added to the repository.

Google's current PlaceAutocompleteElement API is used, without new dependencies: https://developers.google.com/maps/documentation/javascript/place-autocomplete-new

V24 adds nullable place/address fields and independent monthly workplace comparison snapshots. Apply migrations through normal backend startup after testing; do not edit older applied migrations or erase Flyway history. Historical attendance and monthly reports are not reclassified when a workplace changes. Daily attendance keeps browser GPS and existing accuracy, EXIF, camera and AI rules; monthly comparisons use only server-extracted photo GPS. Selecting a company never supplies missing attendance or photo coordinates.

A configured key is required to exercise live search. The selector reports unavailable configuration explicitly rather than displaying fake search results.
