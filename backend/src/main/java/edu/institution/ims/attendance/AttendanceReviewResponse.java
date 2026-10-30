package edu.institution.ims.attendance;

import java.time.Instant;

public record AttendanceReviewResponse(AttendanceReviewStatus status, int revision, Long reviewerId,
        String reviewerName, Instant reviewedAt, String reason, boolean approvalAllowed) {
    public static AttendanceReviewResponse from(AttendanceRecord record) {
        AttendanceReview review = record.getReview();
        return review == null ? new AttendanceReviewResponse(record.isAutomaticAbsent() ? AttendanceReviewStatus.NOT_APPLICABLE
                : AttendanceReviewStatus.PENDING, 0, null, null, null, null, approvalAllowed(record))
                : new AttendanceReviewResponse(review.getStatus(), review.getRevision(), review.getReviewerId(),
                    review.getReviewerName(), review.getReviewedAt(), review.getReason(), approvalAllowed(record));
    }
    public static boolean approvalAllowed(AttendanceRecord record) {
        if (record.isAutomaticAbsent() || record.getAttendanceResult() != AttendanceResult.PRESENT || record.getEvidence() == null) return false;
        var gps = record.getEvidence().getLocationVerificationStatus();
        if (gps != LocationVerificationStatus.VERIFIED && gps != LocationVerificationStatus.NOT_APPLICABLE) return false;
        return record.getSubmissionType() == AttendanceSubmissionType.BASIC || record.getLivenessStatus() == LivenessStatus.VERIFIED;
    }
}
