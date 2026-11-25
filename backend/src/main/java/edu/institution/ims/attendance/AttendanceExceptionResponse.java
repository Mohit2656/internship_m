package edu.institution.ims.attendance;

import java.time.Instant;
import java.time.LocalDate;

public record AttendanceExceptionResponse(
        Long id,
        Long internshipId,
        int internshipNumber,
        String companyName,
        Long studentUserId,
        String studentName,
        String uid,
        LocalDate exceptionDate,
        String exceptionType,
        String reason,
        String status,
        String reviewedByName,
        Instant reviewedAt,
        String reviewRemarks,
        Instant createdAt
) {
    public static AttendanceExceptionResponse from(AttendanceDailyException entity) {
        String studentName = null;
        String uid = null;
        if (entity.getStudent() != null) {
            if (entity.getStudent().getStudentProfile() != null) {
                studentName = entity.getStudent().getStudentProfile().getStudentName();
                uid = entity.getStudent().getStudentProfile().getUid();
            } else {
                studentName = entity.getStudent().getEmail();
            }
        }
        String companyName = entity.getInternship() != null ? entity.getInternship().getCompanyName() : null;
        int internshipNumber = entity.getInternship() != null && entity.getInternship().getOnboarding() != null
                ? entity.getInternship().getOnboarding().getInternshipNumber() : 1;
        String reviewerName = entity.getReviewedBy() != null ? entity.getReviewedBy().getEmail() : null;
        return new AttendanceExceptionResponse(
                entity.getId(),
                entity.getInternship() != null ? entity.getInternship().getId() : null,
                internshipNumber,
                companyName,
                entity.getStudent() != null ? entity.getStudent().getId() : null,
                studentName,
                uid,
                entity.getExceptionDate(),
                entity.getExceptionType().name(),
                entity.getReason(),
                entity.getStatus().name(),
                reviewerName,
                entity.getReviewedAt(),
                entity.getReviewRemarks(),
                entity.getCreatedAt()
        );
    }
}
