package edu.institution.ims.attendance;

/** GPS is an independent location signal; it never determines presence or liveness. */
public enum LocationVerificationStatus {
    VERIFIED,
    OUTSIDE_RADIUS,
    INSUFFICIENT_ACCURACY,
    LOCATION_UNAVAILABLE,
    WORKPLACE_NOT_APPROVED,
    STALE_LOCATION,
    NOT_APPLICABLE
}
