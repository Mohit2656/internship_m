package edu.institution.ims.monthlyprogress;

import java.time.*;

public record MonthlyProgressReportResponse(
        Long id, Long studentUserId, String studentName, String uid, String branch, Integer semester,
        Long internshipId, int internshipNumber, String companyName, String internshipMode, int reportMonthNumber,
        LocalDate periodStart, LocalDate periodEnd, String workDescription,
        String photoOriginalName, String photoMimeType, long photoSize,
        String reportPdfOriginalName, Long reportPdfSize, Instant submittedAt,
        Integer imageWidth, Integer imageHeight, String imageSha256,
        MonthlyProgressStatus status, int revision, Long reviewerId, String reviewerEmail,
        Instant reviewedAt, String rejectionReason) {}
