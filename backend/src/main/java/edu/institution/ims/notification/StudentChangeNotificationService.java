package edu.institution.ims.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.internship.InternshipDetails;
import edu.institution.ims.internship.dto.RegisteredWorkplaceRequest;
import edu.institution.ims.studentprofile.StudentProfile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class StudentChangeNotificationService {
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH).withZone(ZoneId.of("Asia/Kolkata"));

    private final JdbcTemplate jdbc;
    private final MentorNotificationService mentorNotifications;
    private final Clock clock;
    private final ObjectMapper json;

    public StudentChangeNotificationService(JdbcTemplate jdbc,
                                            MentorNotificationService mentorNotifications,
                                            Clock clock,
                                            ObjectMapper json) {
        this.jdbc = jdbc;
        this.mentorNotifications = mentorNotifications;
        this.clock = clock;
        this.json = json;
    }

    public record Change(String field, String before, String after) {}

    @Transactional
    public void notifyProfileChanged(Long studentId, StudentProfile before, StudentProfile after) {
        if (before == null || after == null) return;
        List<Change> changes = new ArrayList<>();
        check(changes, "Student Name", before.getStudentName(), after.getStudentName());
        check(changes, "Roll Number", before.getRollNumber(), after.getRollNumber());
        check(changes, "Section", before.getSection(), after.getSection());
        check(changes, "Permanent Address", before.getPermanentAddress(), after.getPermanentAddress());
        check(changes, "Current Address", before.getCurrentAddress(), after.getCurrentAddress());
        check(changes, "Personal Email", before.getPersonalEmail(), after.getPersonalEmail());
        check(changes, "Phone Number", before.getPhoneNumber(), after.getPhoneNumber());
        check(changes, "Parent/Guardian Name", before.getGuardianName(), after.getGuardianName());
        check(changes, "Parent/Guardian Contact", before.getGuardianContact(), after.getGuardianContact());
        check(changes, "Emergency Contact", before.getEmergencyContact(), after.getEmergencyContact());
        check(changes, "Blood Group", before.getBloodGroup(), after.getBloodGroup());
        check(changes, "Academic Year", before.getAcademicYear(), after.getAcademicYear());
        check(changes, "Semester", before.getSemester(), after.getSemester());
        check(changes, "Faculty Mentor Name", before.getFacultyMentorName(), after.getFacultyMentorName());
        check(changes, "Faculty Mentor Contact", before.getFacultyMentorContact(), after.getFacultyMentorContact());
        check(changes, "Internship Coordinator Name", before.getInternshipCoordinatorName(), after.getInternshipCoordinatorName());
        check(changes, "Internship Coordinator Contact", before.getInternshipCoordinatorContact(), after.getInternshipCoordinatorContact());
        check(changes, "CDC Coordinator Name", before.getCdcCoordinatorName(), after.getCdcCoordinatorName());
        check(changes, "CDC Coordinator Contact", before.getCdcCoordinatorContact(), after.getCdcCoordinatorContact());
        check(changes, "Current CGPA", before.getCurrentCgpa(), after.getCurrentCgpa());

        if (changes.isEmpty()) return;

        Instant now = clock.instant();
        String formattedDate = DATE_FORMATTER.format(now);
        String uid = getStudentUid(studentId);
        String changeSummary = formatChangeSummary(changes);
        String message = "Student " + uid + " updated " + changeSummary + " on " + formattedDate + ".";
        String title = "Profile updated (" + uid + ")";
        String actionUrl = "/mentor/students/" + studentId;
        String eventKey = "profile-update:" + studentId + ":" + now.toEpochMilli();

        audit(studentId, "PROFILE_UPDATE", null, changeSummary, before, after, now);
        mentorNotifications.workflowSubmitted(studentId, eventKey, "STUDENT_PROFILE_UPDATED",
                title, message, actionUrl, now);
    }

    @Transactional
    public void notifyInternshipChanged(Long studentId,
                                        InternshipDetails before,
                                        InternshipDetails after,
                                        RegisteredWorkplaceRequest oldWorkplace,
                                        RegisteredWorkplaceRequest newWorkplace) {
        if (before == null || after == null) return;
        List<Change> changes = new ArrayList<>();
        check(changes, "Company Name", before.getCompanyName(), after.getCompanyName());
        check(changes, "Company Type", before.getCompanyType(), after.getCompanyType());
        check(changes, "Other Company Type", before.getOtherCompanyType(), after.getOtherCompanyType());
        check(changes, "Company Address", before.getCompanyAddress(), after.getCompanyAddress());
        check(changes, "Company Phone", before.getCompanyPhone(), after.getCompanyPhone());
        check(changes, "Supervisor Name", before.getIndustrySupervisorName(), after.getIndustrySupervisorName());
        check(changes, "Supervisor Designation", before.getIndustrySupervisorDesignation(), after.getIndustrySupervisorDesignation());
        check(changes, "Supervisor Contact", before.getIndustrySupervisorContact(), after.getIndustrySupervisorContact());
        check(changes, "Duration (Months)", before.getDurationMonths(), after.getDurationMonths());
        check(changes, "Start Date", before.getStartDate(), after.getStartDate());
        check(changes, "End Date", before.getEndDate(), after.getEndDate());
        check(changes, "Total Weeks", before.getTotalWeeks(), after.getTotalWeeks());
        check(changes, "Internship Mode", before.getInternshipMode(), after.getInternshipMode());
        check(changes, "Stipend", before.getStipendPerMonth(), after.getStipendPerMonth());
        check(changes, "Full-Time Offer", before.isFullTimeEmploymentOffered(), after.isFullTimeEmploymentOffered());

        if (oldWorkplace != null || newWorkplace != null) {
            String oldCoords = oldWorkplace != null && oldWorkplace.latitude() != null && oldWorkplace.longitude() != null
                    ? String.format("(%s, %s)", oldWorkplace.latitude(), oldWorkplace.longitude()) : "None";
            String newCoords = newWorkplace != null && newWorkplace.latitude() != null && newWorkplace.longitude() != null
                    ? String.format("(%s, %s)", newWorkplace.latitude(), newWorkplace.longitude()) : "None";
            if (!Objects.equals(oldCoords, newCoords)) {
                changes.add(new Change("Workplace Coordinates", oldCoords, newCoords));
            }
        }

        if (changes.isEmpty()) return;

        Instant now = clock.instant();
        String formattedDate = DATE_FORMATTER.format(now);
        int internshipNum = after.getOnboarding() != null ? after.getOnboarding().getInternshipNumber() : 1;
        String prefix = internshipNum == 2 ? "Internship 2 " : "";
        String uid = getStudentUid(studentId);
        String changeSummary = formatChangeSummary(changes);
        String message = "Student " + uid + " updated " + prefix + changeSummary + " on " + formattedDate + ".";
        String title = (internshipNum == 2 ? "Internship 2" : "Internship") + " details updated (" + uid + ")";
        String actionUrl = "/mentor/students/" + studentId;
        String eventKey = "internship-" + after.getId() + "-update:" + now.toEpochMilli();

        audit(studentId, internshipNum == 2 ? "INTERNSHIP_2_UPDATE" : "INTERNSHIP_UPDATE",
                after.getId(), changeSummary, before, after, now);
        mentorNotifications.workflowSubmitted(studentId, eventKey, "INTERNSHIP_DETAILS_UPDATED",
                title, message, actionUrl, now);
    }

    @Transactional
    public void notifyProfessionalProfileChanged(Long studentId, String oldHeadline, String newHeadline, String oldInterests, String newInterests) {
        List<Change> changes = new ArrayList<>();
        check(changes, "Professional Headline", oldHeadline, newHeadline);
        check(changes, "Career Interests", oldInterests, newInterests);
        if (changes.isEmpty()) return;

        Instant now = clock.instant();
        String formattedDate = DATE_FORMATTER.format(now);
        String uid = getStudentUid(studentId);
        String changeSummary = formatChangeSummary(changes);
        String message = "Student " + uid + " updated professional profile (" + changeSummary + ") on " + formattedDate + ".";
        String title = "Professional profile updated (" + uid + ")";
        String actionUrl = "/mentor/students/" + studentId;
        String eventKey = "professional-update:" + studentId + ":" + now.toEpochMilli();

        Map<String, String> beforeMap = new HashMap<>();
        beforeMap.put("headline", oldHeadline);
        beforeMap.put("careerInterests", oldInterests);
        Map<String, String> afterMap = new HashMap<>();
        afterMap.put("headline", newHeadline);
        afterMap.put("careerInterests", newInterests);

        audit(studentId, "PROFESSIONAL_UPDATE", null, changeSummary, beforeMap, afterMap, now);
        mentorNotifications.workflowSubmitted(studentId, eventKey, "STUDENT_PROFILE_UPDATED",
                title, message, actionUrl, now);
    }

    @Transactional
    public void notifyResumeUpdated(Long studentId, String filename, boolean isUpdate) {
        Instant now = clock.instant();
        String formattedDate = DATE_FORMATTER.format(now);
        String uid = getStudentUid(studentId);
        String message = "Student " + uid + (isUpdate ? " updated their resume (" : " uploaded a resume (") + filename + ") on " + formattedDate + ".";
        String title = "Resume " + (isUpdate ? "updated" : "uploaded") + " (" + uid + ")";
        String actionUrl = "/mentor/students/" + studentId;
        String eventKey = "resume-upload:" + studentId + ":" + now.toEpochMilli();

        audit(studentId, "RESUME_UPDATE", null, filename, null, filename, now);
        mentorNotifications.workflowSubmitted(studentId, eventKey, "STUDENT_RESUME_UPDATED",
                title, message, actionUrl, now);
    }

    @Transactional
    public void notifySkillsUpdated(Long studentId, String changeSummary) {
        Instant now = clock.instant();
        String formattedDate = DATE_FORMATTER.format(now);
        String uid = getStudentUid(studentId);
        String message = "Student " + uid + " updated skills (" + changeSummary + ") on " + formattedDate + ".";
        String title = "Skills updated (" + uid + ")";
        String actionUrl = "/mentor/students/" + studentId;
        String eventKey = "skills-update:" + studentId + ":" + now.toEpochMilli();

        audit(studentId, "SKILLS_UPDATE", null, changeSummary, null, changeSummary, now);
        mentorNotifications.workflowSubmitted(studentId, eventKey, "STUDENT_SKILLS_UPDATED",
                title, message, actionUrl, now);
    }

    private void check(List<Change> list, String name, Object oldVal, Object newVal) {
        if (oldVal instanceof BigDecimal o && newVal instanceof BigDecimal n) {
            if (o.compareTo(n) == 0) return;
            list.add(new Change(name, o.stripTrailingZeros().toPlainString(), n.stripTrailingZeros().toPlainString()));
            return;
        }
        if (oldVal instanceof Number && newVal instanceof Number) {
            try {
                BigDecimal o = new BigDecimal(oldVal.toString());
                BigDecimal n = new BigDecimal(newVal.toString());
                if (o.compareTo(n) == 0) return;
                list.add(new Change(name, o.stripTrailingZeros().toPlainString(), n.stripTrailingZeros().toPlainString()));
                return;
            } catch (Exception ignored) {}
        }
        String oldStr = oldVal == null ? null : oldVal.toString().trim();
        String newStr = newVal == null ? null : newVal.toString().trim();
        if (!Objects.equals(oldStr, newStr)) {
            list.add(new Change(name, oldStr, newStr));
        }
    }

    private String formatChangeSummary(List<Change> changes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < changes.size(); i++) {
            Change c = changes.get(i);
            if (i > 0) sb.append(", ");
            sb.append(c.field()).append(" from ")
              .append(c.before() == null || c.before().isBlank() ? "None" : c.before())
              .append(" to ")
              .append(c.after() == null || c.after().isBlank() ? "None" : c.after());
        }
        return sb.toString();
    }

    private String getStudentUid(Long studentId) {
        try {
            String uid = jdbc.queryForObject("select coalesce(p.uid, sa.uid) from users u "
                    + "left join student_profiles p on p.user_id=u.id "
                    + "left join student_accounts sa on sa.user_id=u.id where u.id=?", String.class, studentId);
            return uid != null && !uid.isBlank() ? uid : String.valueOf(studentId);
        } catch (Exception e) {
            return String.valueOf(studentId);
        }
    }

    private void audit(Long studentId, String changeType, Long internshipId, String changedFields, Object before, Object after, Instant at) {
        String beforeJson = serialize(before);
        String afterJson = serialize(after);
        jdbc.update("insert into student_change_audit (student_user_id, change_type, internship_id, changed_fields, before_value, after_value, occurred_at) values (?,?,?,?,?,?,?)",
                studentId, changeType, internshipId, changedFields, beforeJson, afterJson, Timestamp.from(at));
    }

    private String serialize(Object obj) {
        if (obj == null) return null;
        try {
            return json.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }
}
