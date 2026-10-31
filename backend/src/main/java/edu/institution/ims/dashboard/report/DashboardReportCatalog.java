package edu.institution.ims.dashboard.report;

import com.fasterxml.jackson.annotation.JsonIgnore;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.studentprofile.Branch;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

/** The only supported export columns and filter expressions. Never accept SQL from a caller. */
@Component
public class DashboardReportCatalog {
    public enum Kind { TEXT, NUMBER, DATE, DATETIME, BOOLEAN }
    public record Field(String id, String label, String category, Kind kind, boolean defaultSelected,
            @JsonIgnore String expression, @JsonIgnore boolean adminOnly) {}
    public record Choice(String value, String label) {}
    public record Filter(String id, String label, String type, List<Choice> choices, String description) {}
    private final List<Field> fields = new ArrayList<>();
    private final JdbcTemplate jdbc;

    public DashboardReportCatalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        add("studentId", "Student account ID", "Student Information", Kind.TEXT, "u.id", false, false);
        add("studentName", "Student name", "Student Information", Kind.TEXT, "coalesce(p.student_name, sa.student_name)", true, false);
        add("uid", "Student UID", "Student Information", Kind.TEXT, "coalesce(p.uid, sa.uid)", true, false);
        add("loginEmail", "Login email", "Student Information", Kind.TEXT, "u.email", true, false);
        profile("rollNumber", "Roll number", "roll_number", "Student Information", false);
        profile("instituteEmail", "Institute email", "institute_email", "Student Information", false);
        profile("personalEmail", "Personal email", "personal_email", "Student Information", false);
        profile("phone", "Phone", "phone_number", "Student Information", false);
        add("profileStatus", "Profile status", "Student Information", Kind.TEXT, "case when p.id is null then 'INCOMPLETE' else 'COMPLETE' end", true, false);
        add("accountActive", "Account active", "Student Information", Kind.BOOLEAN, "u.active", false, true);
        add("passwordChangeRequired", "Initial password change required", "Student Information", Kind.BOOLEAN, "u.must_change_password", false, true);
        add("accountCreatedAt", "Account created at", "Student Information", Kind.DATETIME, "u.created_at", false, true);
        add("profileUpdatedAt", "Profile last updated by student", "Student Information", Kind.DATETIME, "coalesce(p.updated_at, p.created_at)", true, false);
        profile("permanentAddress", "Permanent address", "permanent_address", "Personal and Emergency Contacts", true);
        profile("currentAddress", "Current address", "current_address", "Personal and Emergency Contacts", true);
        profile("guardianName", "Guardian name", "guardian_name", "Personal and Emergency Contacts", true);
        profile("guardianContact", "Guardian contact", "guardian_contact", "Personal and Emergency Contacts", true);
        profile("emergencyContact", "Emergency contact", "emergency_contact", "Personal and Emergency Contacts", true);
        profile("bloodGroup", "Blood group", "blood_group", "Personal and Emergency Contacts", true);
        add("maskedAadhaar", "Aadhaar (masked)", "Personal and Emergency Contacts", Kind.TEXT,
                "case when p.aadhaar_last_four is null then null else concat('XXXX XXXX ', p.aadhaar_last_four) end", false, true);
        add("aadhaarNumber", "Aadhaar number (admin only)", "Personal and Emergency Contacts", Kind.TEXT, "p.aadhaar_encrypted", false, true);
        add("branch", "Branch", "Academic Information", Kind.TEXT, "p.branch", true, false);
        add("academicSession", "Assigned academic session", "Academic Information", Kind.TEXT, "acs.name", false, false);
        add("completionStatus", "Internship completion status", "Internship Completion", Kind.TEXT, "case when i.id is null then null else " + edu.institution.ims.internship.InternshipCompletionService.STATUS_SQL + " end", false, false);
        add("completionDate", "Official completion date", "Internship Completion", Kind.DATETIME, "i.completed_at", false, false);
        add("completionReviewer", "IIIC completion reviewer", "Internship Completion", Kind.TEXT, "cr.email", false, true);
        add("completionRemarks", "Completion remarks", "Internship Completion", Kind.TEXT, "i.completion_remarks", false, true);
        profile("academicYear", "Academic year", "academic_year", "Academic Information", false);
        add("semester", "Semester", "Academic Information", Kind.NUMBER, "coalesce(p.semester, sa.semester)", true, false);
        profile("facultyMentorName", "Faculty mentor (student profile)", "faculty_mentor_name", "Mentor and Coordinators", true);
        profile("facultyMentorContact", "Faculty mentor contact (student profile)", "faculty_mentor_contact", "Mentor and Coordinators", true);
        profile("internshipCoordinatorName", "Internship coordinator", "internship_coordinator_name", "Mentor and Coordinators", true);
        profile("internshipCoordinatorContact", "Internship coordinator contact", "internship_coordinator_contact", "Mentor and Coordinators", true);
        profile("cdcCoordinatorName", "CDC coordinator", "cdc_coordinator_name", "Mentor and Coordinators", true);
        profile("cdcCoordinatorContact", "CDC coordinator contact", "cdc_coordinator_contact", "Mentor and Coordinators", true);
        add("mentorName", "Assigned faculty mentor", "Mentor and Coordinators", Kind.TEXT, "mp.name", true, false);
        add("mentorId", "Assigned mentor account ID", "Mentor and Coordinators", Kind.TEXT, "a.mentor_user_id", false, false);
        add("mentorEmail", "Assigned mentor email", "Mentor and Coordinators", Kind.TEXT, "mu.email", false, false);
        add("mentorEmployeeId", "Mentor employee ID", "Mentor and Coordinators", Kind.TEXT, "mp.employee_id", false, false);
        add("mentorDepartment", "Mentor department", "Mentor and Coordinators", Kind.TEXT, "mp.department", false, false);
        add("mentorDesignation", "Mentor designation", "Mentor and Coordinators", Kind.TEXT, "mp.designation", false, false);
        add("mentorPhone", "Mentor phone", "Mentor and Coordinators", Kind.TEXT, "mp.phone", false, false);
        add("assignedAt", "Mentor assigned at", "Mentor and Coordinators", Kind.DATETIME, "a.assigned_at", false, false);
        related("assignmentHistory", "Mentor assignment history", "Mentor and Coordinators", true);
        add("workflowStatus", "Internship workflow status", "Internship", Kind.TEXT, "coalesce(o.workflow_status, 'NOT_SECURED')", true, false);
        add("internshipSource", "Internship source", "Internship", Kind.TEXT, "o.source", false, false);
        add("securedAt", "Internship secured at", "Internship", Kind.DATETIME, "o.secured_at", false, false);
        add("diaryIssued", "Diary issued", "Documents", Kind.BOOLEAN, "o.diary_issued", false, false);
        add("diaryIssuedAt", "Diary issued at", "Documents", Kind.DATETIME, "o.diary_issued_at", false, false);
        add("documentsCompletedAt", "Documents completed at", "Documents", Kind.DATETIME, "o.documents_completed_at", false, false);
        related("documents", "Document checklist, filenames and upload dates", "Documents", false);
        add("documentsCount", "Uploaded documents (of 4)", "Documents", Kind.NUMBER, null, false, false);
        add("companyName", "Company name", "Company Information", Kind.TEXT, "i.company_name", true, false);
        internship("companyType", "Company type", "company_type", "Company Information", Kind.TEXT);
        internship("otherCompanyType", "Other company type", "other_company_type", "Company Information", Kind.TEXT);
        internship("companyAddress", "Company address", "company_address", "Company Information", Kind.TEXT);
        internship("companyPhone", "Company phone", "company_phone", "Company Information", Kind.TEXT);
        internship("supervisorName", "Industry supervisor", "industry_supervisor_name", "Company Information", Kind.TEXT);
        internship("supervisorDesignation", "Supervisor designation", "industry_supervisor_designation", "Company Information", Kind.TEXT);
        internship("supervisorContact", "Supervisor contact", "industry_supervisor_contact", "Company Information", Kind.TEXT);
        add("internshipMode", "Internship mode", "Internship", Kind.TEXT, "i.internship_mode", true, false);
        add("internshipUpdatedAt", "Internship last updated by student", "Internship", Kind.DATETIME, "coalesce(i.updated_at, i.created_at)", true, false);
        internship("startDate", "Internship start date", "start_date", "Internship", Kind.DATE);
        internship("endDate", "Internship end date", "end_date", "Internship", Kind.DATE);
        internship("durationMonths", "Duration (months)", "duration_months", "Internship", Kind.NUMBER);
        internship("totalWeeks", "Duration (weeks)", "total_weeks", "Internship", Kind.NUMBER);
        internship("stipend", "Monthly stipend (INR)", "stipend_per_month", "Internship", Kind.NUMBER);
        internship("fullTimeOffered", "Full-time employment offered", "full_time_employment_offered", "Internship", Kind.BOOLEAN);
        related("hybridSchedule", "Hybrid weekly schedule", "Internship", false);
        add("attendanceDate", "Attendance reference date", "Attendance", Kind.DATE, "cast(:attendanceDate as date)", false, false);
        add("attendanceStatus", "Attendance on reference date", "Attendance", Kind.TEXT, "coalesce(t.attendance_result, 'NOT_SUBMITTED')", true, false);
        add("verificationStatus", "Liveness on reference date", "Attendance", Kind.TEXT, AttendanceLivenessSql.status("t", "te"), true, false);
        add("submissionType", "Attendance submission type", "Attendance", Kind.TEXT, "t.submission_type", false, false);
        add("automaticAbsent", "Automatically recorded absence", "Attendance", Kind.BOOLEAN, "t.submission_type = 'AUTO_ABSENT'", false, false);
        add("attendanceSubmittedAt", "Attendance last updated by student", "Attendance", Kind.DATETIME, "case when t.submission_type <> 'AUTO_ABSENT' then t.server_submitted_at end", false, false);
        add("attendanceGeneratedAt", "Absence automatically recorded at", "Attendance", Kind.DATETIME, "case when t.submission_type = 'AUTO_ABSENT' then t.server_submitted_at end", false, false);
        add("todayWorkMode", "Work mode on reference date", "Attendance", Kind.TEXT,
                "case when i.internship_mode = 'HYBRID' then hs.work_mode when i.internship_mode = 'OFFICE_REPORTING' then 'OFFICE' when i.internship_mode = 'ONLINE' then 'REMOTE' else null end", false, false);
        for (String id : List.of("attendanceCount", "presentCount", "absentCount", "verifiedCount", "unverifiedCount", "notPerformedCount", "historicalCount"))
            add(id, switch (id) { case "attendanceCount" -> "Daily attendance records"; case "presentCount" -> "Present records";
                case "absentCount" -> "Absent records"; case "verifiedCount" -> "Liveness verified records";
                case "unverifiedCount" -> "Liveness failed records"; case "notPerformedCount" -> "Liveness not performed records";
                default -> "Historical verification records"; }, "Attendance History", Kind.NUMBER, null, false, true);
        related("attendanceHistory", "Attendance history (date, result, liveness, submission)", "Attendance History", true);
        related("attendanceEvidence", "Attendance liveness evidence details", "Attendance History", true);
        add("headline", "Professional headline", "Career and Skills", Kind.TEXT, "pp.headline", false, false);
        add("careerInterests", "Career interests", "Career and Skills", Kind.TEXT, "pp.career_interests", false, false);
        related("skills", "Confirmed and manually added skills", "Career and Skills", false);
        related("skillProvenance", "Skill sources", "Career and Skills", true);
        related("skillSuggestions", "Current resume skill suggestions and review status", "Career and Skills", true);
        add("resumeFilename", "Active resume filename", "Resume and AI Analysis", Kind.TEXT, "r.original_filename", false, true);
        add("resumeUploadedAt", "Active resume uploaded at", "Resume and AI Analysis", Kind.DATETIME, "r.uploaded_at", false, true);
        add("resumeStatus", "Active resume parsing status", "Resume and AI Analysis", Kind.TEXT, "r.parsing_status", false, true);
        add("resumeSize", "Active resume size (bytes)", "Resume and AI Analysis", Kind.NUMBER, "r.file_size", false, true);
        add("resumeContentType", "Resume content type", "Resume and AI Analysis", Kind.TEXT, "r.content_type", false, true);
        add("analysisModel", "Resume AI model", "Resume and AI Analysis", Kind.TEXT, "ra.model_name", false, true);
        add("analysisVersion", "Resume AI model version", "Resume and AI Analysis", Kind.TEXT, "ra.model_version", false, true);
        add("analysisCompletedAt", "Resume analyzed at", "Resume and AI Analysis", Kind.DATETIME, "ra.completed_at", false, true);
        related("extractedSkills", "AI extracted skills (unreviewed analysis)", "Resume and AI Analysis", true);
        related("resumeHistory", "Resume version history (filename, status, active, upload date)", "Resume and AI Analysis", true);
        related("experience", "AI extracted experience", "Resume and AI Analysis", true);
        related("education", "AI extracted education", "Resume and AI Analysis", true);
        related("certifications", "AI extracted certifications", "Resume and AI Analysis", true);
        add("monthlyReportCount", "Monthly progress reports submitted", "Monthly Progress", Kind.NUMBER, null, false, false);
        related("monthlyReports", "Monthly report periods, work and submission dates", "Monthly Progress", false);
        related("monthlyPhotos", "Monthly report photo metadata", "Monthly Progress", false);
    }
    private void add(String id, String label, String category, Kind kind, String expression, boolean selected, boolean adminOnly) {
        fields.add(new Field(id, label, category, kind, selected, expression, adminOnly));
    }
    private void profile(String id, String label, String column, String category, boolean adminOnly) { add(id, label, category, Kind.TEXT, "p." + column, false, adminOnly); }
    private void internship(String id, String label, String column, String category, Kind kind) { add(id, label, category, kind, "i." + column, false, true); }
    private void related(String id, String label, String category, boolean adminOnly) { add(id, label, category, Kind.TEXT, null, false, adminOnly); }
    public boolean isAdmin(UserPrincipal user) {
        if (!Set.of("ADMIN", "MENTOR").contains(user.role())) throw new AccessDeniedException("Dashboard reports require an administrator or mentor");
        return user.role().equals("ADMIN");
    }
    public List<Field> fields(UserPrincipal user) { boolean admin = isAdmin(user); return fields.stream().filter(f -> admin || !f.adminOnly()).toList(); }
    public List<Field> selected(UserPrincipal user, List<String> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > fields.size() || new HashSet<>(ids).size() != ids.size())
            throw new IllegalArgumentException("Select at least one field, without duplicates");
        Map<String, Field> allowed = new HashMap<>(); fields(user).forEach(f -> allowed.put(f.id(), f));
        return ids.stream().map(id -> { if (!allowed.containsKey(id)) throw new IllegalArgumentException("Unsupported or unauthorized report field: " + id); return allowed.get(id); }).toList();
    }
    public List<Filter> filters(UserPrincipal user) {
        boolean admin = isAdmin(user);
        List<Filter> result = new ArrayList<>();
        result.add(new Filter("search", "Student name, UID or email", "text", List.of(), "Contains text, case insensitive"));
        result.add(new Filter("studentId", "Student account ID", "number", List.of(), "Optional single student; checked against your access"));
        result.add(new Filter("branch", "Branch", "select", Arrays.stream(Branch.values()).map(b -> new Choice(b.name(), b.getLabel())).toList(), ""));
        result.add(new Filter("completionSearch", "Completion student / UID / company", "text", List.of(), "Contains text, case insensitive"));
        String sessionSql="select s.id,s.name from academic_sessions s";
        if (!admin) sessionSql+=" where exists(select 1 from users u join mentor_student_assignments a on a.student_user_id=u.id and a.active=true where u.academic_session_id=s.id and a.mentor_user_id=?)";
        List<Choice> sessions=jdbc.query(sessionSql+" order by s.name desc,s.id desc",(rs,row)->new Choice(Long.toString(rs.getLong("id")),rs.getString("name")),admin?new Object[]{}:new Object[]{user.id()});
        result.add(new Filter("academicSessionId", "Assigned academic session", "select", sessions, "Uses the student's assigned session, including closed historical sessions; independent of profile academic year"));
        choice(result, "completionStatus", "Internship completion", "ALL", "NOT_STARTED", "IN_PROGRESS", "COMPLETION_PENDING", "COMPLETED");
        result.add(new Filter("academicYear", "Profile academic year", "text", List.of(), "Student-entered profile year, e.g. 2025-2026; independent of the assigned academic session"));
        result.add(new Filter("semester", "Semester", "select", java.util.stream.IntStream.rangeClosed(1, 8).mapToObj(n -> new Choice("" + n, "" + n)).toList(), ""));
        choice(result, "profileStatus", "Profile status", "COMPLETE", "INCOMPLETE");
        choice(result, "workflowStatus", "Internship workflow", "NOT_SECURED", "INTERNSHIP_SECURED", "DOCUMENTS_PENDING", "DOCUMENTS_COMPLETE", "DIARY_ISSUED", "INTERNSHIP_DETAILS_COMPLETE");
        result.add(new Filter("company", "Company", "text", List.of(), "Contains text, case insensitive"));
        choice(result, "internshipMode", "Internship mode", "OFFICE_REPORTING", "HYBRID", "ONLINE", "COLLEGE_REPORTING");
        choice(result, "internshipSource", "Internship source", "DEPARTMENT", "CDC", "SELF");
        choice(result, "attendanceStatus", "Attendance on reference date", "PRESENT", "ABSENT", "NOT_SUBMITTED");
        choice(result, "verificationStatus", "Liveness on reference date", "VERIFIED", "FAILED", "NOT_PERFORMED", "HISTORICAL", "NOT_APPLICABLE", "NOT_SUBMITTED");
        if (admin) {
            result.add(new Filter("mentorId", "Assigned mentor account ID", "number", List.of(), ""));
            choice(result, "mentorAssignment", "Mentor assignment", "ASSIGNED", "UNASSIGNED");
            choice(result, "companyType", "Company type", "IT", "MANUFACTURING", "CORE", "OTHER");
            choice(result, "paid", "Stipend", "PAID", "UNPAID");
            choice(result, "fullTimeOffered", "Full-time employment offered", "YES", "NO");
            choice(result, "accountActive", "Account active", "YES", "NO");
            result.add(new Filter("attendanceDate", "Attendance reference date", "date", List.of(), "Defaults to today in the institution timezone"));
            result.add(new Filter("historyFrom", "History from", "date", List.of(), "Limits attendance history and monthly reports by attendance date / period start"));
            result.add(new Filter("historyTo", "History through", "date", List.of(), "Inclusive; does not exclude students without history"));
        }
        return result;
    }
    private void choice(List<Filter> result, String id, String label, String... values) {
        result.add(new Filter(id, label, "select", Arrays.stream(values).map(v -> new Choice(v, v.replace('_', ' '))).toList(), ""));
    }
    /** One normalization rule for dashboard exports and institutional report tables. */
    static String filterValue(Filter filter,String raw) {
        if(raw==null || raw.length()>254) throw new IllegalArgumentException("Invalid value for "+filter.label());
        String value=raw.trim();
        if(value.isEmpty() || (filter.type().equals("select") && value.equalsIgnoreCase("ALL"))) return "";
        if(filter.type().equals("select")) return filter.choices().stream().filter(c->c.value().equalsIgnoreCase(value)).findFirst()
                .orElseThrow(()->new IllegalArgumentException("Invalid value for "+filter.label())).value();
        return value;
    }
}
