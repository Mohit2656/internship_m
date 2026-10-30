# Opportunity matching and saved reports

Publication runs local matching in the publication database transaction. It uses bulk-loaded current student profiles, active resumes and active confirmed skills. Rankings cover active students with completed institutional profiles. Missing institutional profiles are omitted from coordinator rankings, return UNKNOWN on the student's own score endpoint, and cannot receive strong-match notifications. The AI service is not called; its existing semantic evidence endpoints remain available independently. A failed transaction rolls back the publication, report and inbox events together. Retrying publication does not create another report.

The algorithm `exact-skills-v1` gives required skills 80% and preferred skills 20%. For each group, coverage is the number of exact normalized matches divided by the number of listed skills. When a group is unspecified its weight is removed and the remaining weight rescaled to 100%. When both groups are unspecified the score is zero. Scores are rounded to two decimal places. Java and JavaScript, for example, remain different skills. No model-generated percentages or sensitive personal attributes enter scoring.

Matching reads `ResumeCorrectionService.effectiveValues` for the current parsed resume. Its latest correction overlay replaces the original extracted skills; removed original AI skills are not unioned back in. Explicit `STUDENT_ADDED` confirmed profile skills remain independently authoritative. In the absence of a parsed active resume, the existing active confirmed profile skills are used. The original extraction, uploaded file, and existing skill confirmation/suggestion APIs are unchanged.

Branch and semester restrictions are evaluated using the institutional profile. A configured minimum CGPA remains UNKNOWN because the existing authoritative student profile has no CGPA field. A known branch/semester mismatch remains INELIGIBLE regardless of skill score. Education, experience roles/companies and certifications from the effective resume appear as supporting reviewer context without contributing invented qualifications, experience years or inferred skills. The existing parser has no project field and opportunities have no structured degree/experience/certification requirements. Only configured branch, semester, CGPA and skill criteria are evaluated; reviewers must check other requirements in the description manually.

Strong-match notifications require ELIGIBLE status, every required skill, at least one matched configured skill, and the configured score threshold (75 by default). Set the optional Render environment variable `OPPORTUNITY_STRONG_MATCH_THRESHOLD` (Spring property `app.opportunity-matching.strong-match-threshold`) to a value from 0 to 100. No new setting is required for the default. Draft matching creates reports without student alerts. Closed reports remain readable and cannot be recalculated. Expired opportunities do not generate match alerts. Notification text identifies the organization, opportunity, score, matched skills and next step, and explicitly states that the recommendation is not company selection.

The existing student inbox and unique event key suppress duplicates across repeated runs. A material match is defined by the five-point score band and sorted matched required/preferred skills; an unchanged fingerprint never produces another alert. A changed strong match produces a new history entry and alert. An old fingerprint returning later does not alert again.

Notification recipients are processed in ascending student ID order, independently of candidate ranking, to keep foreign-key user locks consistent with batch mentor assignment updates.

Migration V38 adds append-only `opportunity_matching_runs` containing algorithm version, trigger, database timestamp and report snapshot. These snapshots intentionally retain the candidate identity and matching evidence at the time of the run; no resume file or unrelated personal profile data is copied. Current profile/resume links can differ from historical evidence. The IIIC table supports score sorting, name/UID/skill search, branch/eligibility filtering, authorized resume download, and CSV export with spreadsheet-formula neutralization.

APIs:

- `GET /api/v1/admin/opportunities/{id}/matching-runs`: history summaries.
- `GET /api/v1/admin/opportunities/{id}/matching-runs/{runId}`: opportunity-scoped immutable report.
- `POST /api/v1/admin/opportunities/{id}/matching-runs`: manually recalculate (also supports draft review).
- `GET /api/v1/student/opportunities/{id}/matching-score`: current score for the authenticated student only; active published opportunities only.

Legacy talent discovery and semantic matching APIs remain backward compatible, and consume the same effective corrected resume skills/experience while preserving the original-extraction APIs. Run history uses existing ADMIN/IIIC authorization; no student can retrieve another student's report.
