# Mentor attendance review

Mentor decisions are stored separately from the original daily attendance, location evidence and liveness outcome. Existing attendance totals continue to describe submitted present/absent days; a mentor decision never turns a failed GPS result into a verified one.

An attendance entry starts as `PENDING` without creating a review row. Automatic absences display `NOT_APPLICABLE`. Active assigned mentors can approve, reject or flag a submitted present entry for `MANUAL_REVIEW`. Rejecting or requesting manual review requires a reason of 5–1000 characters. Manual review can subsequently be approved or rejected. Approved/rejected decisions are final. Repeated decisions and stale revisions return a conflict.

Approval requires the original location verification status to be `VERIFIED` or `NOT_APPLICABLE`. Camera capture submissions also require the existing liveness result to be `VERIFIED`. Missing/failed GPS checks, missing workplace approval, and insufficient capture verification allow rejection or manual review only. The existing one-submission-per-day rule remains; a manual-review message asks the student to contact the mentor.

`POST /api/v1/mentor/attendance/{id}/review` accepts `status`, `revision` and optional `reason`. Review audit history is available at `/{mentor|admin|student}/attendance/{id}/review/history` beneath `/api/v1`, with current assignment, IIIC role or student ownership checks. Attendance responses include a nested review summary. The student directory includes submitted/GPS verified/pending/approved/rejected/manual-review counts.

V39 adds a latest-decision table and an append-only audit table. Each revision records the assignment, reviewer identity/name, timestamp, reason and transition. The decision, audit entry and student notification commit together. The event key `attendance-review:{id}:{revision}` prevents duplicate inbox events. No historical attendance/evidence rows are rewritten by the migration.

Assigned mentors can read resume metadata, original analysis, effective corrections/history and download the original file through `/api/v1/mentor/students/{studentId}/professional-profile`, `/resume/analysis`, `/resume/analysis/corrections` and `/resume/download`. These mentor routes expose no correction mutation operation.
