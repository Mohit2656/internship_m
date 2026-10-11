package edu.institution.ims.dashboard.report;

/** Projection of the current liveness policy; legacy location decisions are never reassessed. */
final class AttendanceLivenessSql {
    private AttendanceLivenessSql() {}

    static String status(String attendance, String evidence) {
        return "case when " + attendance + ".id is null then 'NOT_SUBMITTED'"
                + " when " + attendance + ".verification_basis <> 'LIVENESS' then 'HISTORICAL'"
                + " when " + evidence + ".id is null or " + evidence + ".ai_verification_session_id is null or "
                + evidence + ".ai_processed_at is null then 'NOT_PERFORMED'"
                + " when " + attendance + ".verification_status = 'VERIFIED' and "
                + evidence + ".ai_face_detected = true and " + evidence + ".ai_single_face = true and "
                + evidence + ".ai_quality_passed = true and " + evidence + ".ai_liveness_passed = true and "
                + evidence + ".ai_failure_reason is null then 'VERIFIED' else 'FAILED' end";
    }

    static String reason(String attendance, String evidence) {
        return "case (" + status(attendance, evidence) + ")"
                + " when 'HISTORICAL' then 'Historical verification retained; liveness not reassessed'"
                + " when 'NOT_PERFORMED' then 'EDGE_AI_EVIDENCE_MISSING'"
                + " when 'VERIFIED' then 'EDGE_AI_VERIFIED'"
                + " when 'FAILED' then coalesce(" + evidence + ".ai_failure_reason, 'EDGE_AI_LIVENESS_FAILED')"
                + " else null end";
    }
}
