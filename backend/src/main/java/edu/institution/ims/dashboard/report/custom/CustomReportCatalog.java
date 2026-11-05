package edu.institution.ims.dashboard.report.custom;

import com.fasterxml.jackson.annotation.JsonIgnore;
import edu.institution.ims.studentprofile.Branch;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CustomReportCatalog {

    public enum Kind { TEXT, NUMBER, DATE, DATETIME, BOOLEAN, CATEGORICAL }

    public record Choice(String value, String label) {}

    public record Field(
            String id,
            String label,
            String category,
            Kind kind,
            List<Choice> choices,
            boolean dynamicCompany,
            boolean aggregatable,
            List<String> allowedOperators,
            @JsonIgnore String expression
    ) {}

    private final Map<String, Field> fieldsMap = new LinkedHashMap<>();

    // Standard operator lists per data type
    public static final List<String> NUMBER_OPERATORS = List.of(
            "EQUALS", "NOT_EQUALS", "GREATER_THAN", "GREATER_THAN_OR_EQUAL",
            "LESS_THAN", "LESS_THAN_OR_EQUAL", "BETWEEN", "IS_EMPTY", "IS_NOT_EMPTY"
    );

    public static final List<String> TEXT_OPERATORS = List.of(
            "EQUALS", "NOT_EQUALS", "CONTAINS", "STARTS_WITH", "ENDS_WITH", "IS_EMPTY", "IS_NOT_EMPTY"
    );

    public static final List<String> CATEGORICAL_OPERATORS = List.of(
            "IS", "IS_NOT", "IS_ANY_OF", "IS_NONE_OF"
    );

    public static final List<String> DATE_OPERATORS = List.of(
            "BEFORE", "AFTER", "ON", "BETWEEN", "IS_EMPTY", "IS_NOT_EMPTY"
    );

    public static final List<String> BOOLEAN_OPERATORS = List.of(
            "YES", "NO"
    );

    public CustomReportCatalog() {
        // --- A. Student Information ---
        add("studentName", "Student Name", "Student Information", Kind.TEXT,
                null, false, false, TEXT_OPERATORS, "coalesce(p.student_name, sa.student_name, u.email)");
        add("uid", "Student UID / Enrollment No.", "Student Information", Kind.TEXT,
                null, false, false, TEXT_OPERATORS, "coalesce(p.uid, sa.uid)");
        add("rollNumber", "Roll Number", "Student Information", Kind.TEXT,
                null, false, false, TEXT_OPERATORS, "p.roll_number");
        add("branch", "Branch", "Student Information", Kind.CATEGORICAL,
                branchChoices(), false, false, CATEGORICAL_OPERATORS, "p.branch");
        add("semester", "Semester", "Student Information", Kind.NUMBER,
                semesterChoices(), false, false, NUMBER_OPERATORS, "coalesce(p.semester, sa.semester)");
        add("academicYear", "Academic Year", "Student Information", Kind.TEXT,
                null, false, false, TEXT_OPERATORS, "p.academic_year");
        add("academicSession", "Academic Session", "Student Information", Kind.TEXT,
                null, false, false, TEXT_OPERATORS, "acs.name");
        add("profileStatus", "Profile Completion Status", "Student Information", Kind.CATEGORICAL,
                List.of(new Choice("COMPLETE", "Complete"), new Choice("INCOMPLETE", "Incomplete")),
                false, false, CATEGORICAL_OPERATORS, "case when p.id is null then 'INCOMPLETE' else 'COMPLETE' end");
        add("loginEmail", "Login Email", "Student Information", Kind.TEXT,
                null, false, false, TEXT_OPERATORS, "u.email");
        add("instituteEmail", "Institute Email", "Student Information", Kind.TEXT,
                null, false, false, TEXT_OPERATORS, "p.institute_email");
        add("personalEmail", "Personal Email", "Student Information", Kind.TEXT,
                null, false, false, TEXT_OPERATORS, "p.personal_email");
        add("phone", "Phone Number", "Student Information", Kind.TEXT,
                null, false, false, TEXT_OPERATORS, "p.phone_number");
        add("currentAddress", "Current Address", "Student Information", Kind.TEXT,
                null, false, false, TEXT_OPERATORS, "p.current_address");
        add("permanentAddress", "Permanent Address", "Student Information", Kind.TEXT,
                null, false, false, TEXT_OPERATORS, "p.permanent_address");
        add("bloodGroup", "Blood Group", "Student Information", Kind.TEXT,
                null, false, false, TEXT_OPERATORS, "p.blood_group");
        add("currentCgpa", "Current CGPA", "Student Information", Kind.NUMBER,
                null, false, true, NUMBER_OPERATORS, "p.current_cgpa");

        // --- B. Internship Information ---
        add("internshipNumber", "Internship Slot", "Internship Information", Kind.NUMBER,
                List.of(new Choice("1", "Internship 1"), new Choice("2", "Internship 2")),
                false, false, NUMBER_OPERATORS, "coalesce(o.internship_number, 1)");
        add("companyName", "Company Name", "Internship Information", Kind.TEXT,
                null, true, false, TEXT_OPERATORS, "i.company_name");
        add("companyType", "Company Type", "Internship Information", Kind.CATEGORICAL,
                List.of(new Choice("IT", "IT"), new Choice("MANUFACTURING", "Manufacturing"), new Choice("CORE", "Core"), new Choice("OTHER", "Other")),
                false, false, CATEGORICAL_OPERATORS, "i.company_type");
        add("internshipSource", "Internship Source", "Internship Information", Kind.CATEGORICAL,
                List.of(new Choice("DEPARTMENT", "Department"), new Choice("CDC", "CDC"), new Choice("SELF", "Self")),
                false, false, CATEGORICAL_OPERATORS, "o.source");
        add("internshipMode", "Internship Mode", "Internship Information", Kind.CATEGORICAL,
                List.of(new Choice("OFFICE_REPORTING", "Office Reporting"), new Choice("HYBRID", "Hybrid"), new Choice("ONLINE", "Online"), new Choice("COLLEGE_REPORTING", "College Reporting")),
                false, false, CATEGORICAL_OPERATORS, "i.internship_mode");
        add("startDate", "Internship Start Date", "Internship Information", Kind.DATE,
                null, false, false, DATE_OPERATORS, "i.start_date");
        add("endDate", "Internship End Date", "Internship Information", Kind.DATE,
                null, false, false, DATE_OPERATORS, "i.end_date");
        add("durationMonths", "Duration (Months)", "Internship Information", Kind.NUMBER,
                null, false, true, NUMBER_OPERATORS, "i.duration_months");
        add("totalWeeks", "Duration (Weeks)", "Internship Information", Kind.NUMBER,
                null, false, true, NUMBER_OPERATORS, "i.total_weeks");
        add("workflowStatus", "Internship Workflow Status", "Internship Information", Kind.CATEGORICAL,
                List.of(new Choice("NOT_SECURED", "Not Secured"), new Choice("INTERNSHIP_SECURED", "Internship Secured"),
                        new Choice("DOCUMENTS_PENDING", "Documents Pending"), new Choice("DOCUMENTS_COMPLETE", "Documents Complete"),
                        new Choice("DIARY_ISSUED", "Diary Issued"), new Choice("INTERNSHIP_DETAILS_COMPLETE", "Internship Details Complete")),
                false, false, CATEGORICAL_OPERATORS, "coalesce(o.workflow_status, 'NOT_SECURED')");
        add("completionStatus", "Internship Completion Status", "Internship Information", Kind.CATEGORICAL,
                List.of(new Choice("NOT_STARTED", "Not Started"), new Choice("IN_PROGRESS", "In Progress"),
                        new Choice("COMPLETION_PENDING", "Completion Pending"), new Choice("COMPLETED", "Completed")),
                false, false, CATEGORICAL_OPERATORS, "case when i.id is null then null else " + edu.institution.ims.internship.InternshipCompletionService.STATUS_SQL + " end");
        add("completionDate", "Completion Date", "Internship Information", Kind.DATETIME,
                null, false, false, DATE_OPERATORS, "i.completed_at");
        add("industrySupervisorName", "Industry Supervisor", "Internship Information", Kind.TEXT,
                null, false, false, TEXT_OPERATORS, "i.industry_supervisor_name");
        add("industrySupervisorDesignation", "Supervisor Designation", "Internship Information", Kind.TEXT,
                null, false, false, TEXT_OPERATORS, "i.industry_supervisor_designation");
        add("industrySupervisorContact", "Supervisor Contact", "Internship Information", Kind.TEXT,
                null, false, false, TEXT_OPERATORS, "i.industry_supervisor_contact");

        // --- C. Financial Information ---
        add("stipend", "Monthly Stipend (INR)", "Financial Information", Kind.NUMBER,
                null, false, true, NUMBER_OPERATORS, "coalesce(i.stipend_per_month, 0)");
        add("paid", "Paid / Unpaid Status", "Financial Information", Kind.CATEGORICAL,
                List.of(new Choice("PAID", "Paid"), new Choice("UNPAID", "Unpaid")),
                false, false, CATEGORICAL_OPERATORS, "case when i.stipend_per_month > 0 then 'PAID' else 'UNPAID' end");
        add("annualCtc", "Annual CTC (LPA)", "Financial Information", Kind.NUMBER,
                null, false, true, NUMBER_OPERATORS, "round((coalesce(i.stipend_per_month, 0) * 12.0) / 100000.0, 2)");
        add("fullTimeOffered", "Full-time Offer Status", "Financial Information", Kind.BOOLEAN,
                null, false, false, BOOLEAN_OPERATORS, "i.full_time_employment_offered");

        // --- D. Attendance Information ---
        add("presentDays", "Present Days", "Attendance Information", Kind.NUMBER,
                null, false, true, NUMBER_OPERATORS, "coalesce(att.present_days, 0)");
        add("absentDays", "Absent Days", "Attendance Information", Kind.NUMBER,
                null, false, true, NUMBER_OPERATORS, "coalesce(att.absent_days, 0)");
        add("totalDays", "Applicable Working Days", "Attendance Information", Kind.NUMBER,
                null, false, true, NUMBER_OPERATORS, "coalesce(att.total_days, 0)");
        add("attendancePercentage", "Attendance Percentage (%)", "Attendance Information", Kind.NUMBER,
                null, false, true, NUMBER_OPERATORS, "att.attendance_percentage");
        add("lateSubmissionCount", "Late Attendance Submissions", "Attendance Information", Kind.NUMBER,
                null, false, true, NUMBER_OPERATORS, "coalesce(att.late_submissions, 0)");

        // --- E. Mentor Information ---
        add("mentorName", "Faculty Mentor Name", "Mentor Information", Kind.TEXT,
                null, false, false, TEXT_OPERATORS, "mp.name");
        add("mentorEmail", "Faculty Mentor Email", "Mentor Information", Kind.TEXT,
                null, false, false, TEXT_OPERATORS, "mu.email");
        add("mentorDepartment", "Mentor Department", "Mentor Information", Kind.TEXT,
                null, false, false, TEXT_OPERATORS, "mp.department");
        add("mentorDesignation", "Mentor Designation", "Mentor Information", Kind.TEXT,
                null, false, false, TEXT_OPERATORS, "mp.designation");
        add("mentorPhone", "Mentor Phone", "Mentor Information", Kind.TEXT,
                null, false, false, TEXT_OPERATORS, "mp.phone");
        add("mentorAssigned", "Mentor Assignment Status", "Mentor Information", Kind.CATEGORICAL,
                List.of(new Choice("ASSIGNED", "Assigned"), new Choice("UNASSIGNED", "Unassigned")),
                false, false, CATEGORICAL_OPERATORS, "case when a.id is not null then 'ASSIGNED' else 'UNASSIGNED' end");
        add("workplaceApprovalStatus", "Workplace Approval Status", "Mentor Information", Kind.CATEGORICAL,
                List.of(new Choice("APPROVED", "Approved"), new Choice("PENDING", "Pending"), new Choice("NOT_CONFIGURED", "Not Configured")),
                false, false, CATEGORICAL_OPERATORS, "case when loc.id is null then 'NOT_CONFIGURED' when loc.verified = true then 'APPROVED' else 'PENDING' end");

        // --- F. Progress and Document Information ---
        add("weeklyProgressCount", "Weekly Reports Submitted", "Progress and Document Information", Kind.NUMBER,
                null, false, true, NUMBER_OPERATORS, "coalesce(wp.weekly_count, 0)");
        add("monthlyProgressCount", "Approved Monthly Reports", "Progress and Document Information", Kind.NUMBER,
                null, false, true, NUMBER_OPERATORS, "coalesce(mp_rep.monthly_count, 0)");
        add("documentsCompleted", "Documents Completed Count", "Progress and Document Information", Kind.NUMBER,
                null, false, true, NUMBER_OPERATORS, "coalesce(docs.docs_count, 0)");
        add("diaryIssued", "Internship Diary Issued Status", "Progress and Document Information", Kind.BOOLEAN,
                null, false, false, BOOLEAN_OPERATORS, "coalesce(o.diary_issued, false)");
        add("diaryIssuedAt", "Diary Issued At", "Progress and Document Information", Kind.DATETIME,
                null, false, false, DATE_OPERATORS, "o.diary_issued_at");
    }

    private void add(String id, String label, String category, Kind kind, List<Choice> choices,
                     boolean dynamicCompany, boolean aggregatable, List<String> operators, String expression) {
        fieldsMap.put(id, new Field(id, label, category, kind, choices, dynamicCompany, aggregatable, operators, expression));
    }

    private static List<Choice> branchChoices() {
        return Arrays.stream(Branch.values())
                .map(b -> new Choice(b.name(), b.getLabel()))
                .toList();
    }

    private static List<Choice> semesterChoices() {
        List<Choice> list = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            list.add(new Choice(String.valueOf(i), "Semester " + i));
        }
        return list;
    }

    public List<Field> getAllFields() {
        return new ArrayList<>(fieldsMap.values());
    }

    public Field getField(String id) {
        return fieldsMap.get(id);
    }

    public boolean hasField(String id) {
        return fieldsMap.containsKey(id);
    }
}
