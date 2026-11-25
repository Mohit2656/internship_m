package edu.institution.ims.attendance;

import edu.institution.ims.internship.*;
import edu.institution.ims.mentor.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class StudentAttendanceDirectoryService {
    private final UserRepository users;
    private final MentorStudentAssignmentRepository assignments;
    private final MentorProfileRepository mentorProfiles;
    private final InternshipDetailsRepository internships;
    private final AttendanceRepository attendance;
    private final AttendancePolicyService policies;
    private final AttendanceCalendarProperties calendar;
    private final Clock clock;
    private final AttendanceDailyExceptionRepository exceptions;

    @org.springframework.beans.factory.annotation.Autowired
    public StudentAttendanceDirectoryService(UserRepository users, MentorStudentAssignmentRepository assignments,
            MentorProfileRepository mentorProfiles,
            InternshipDetailsRepository internships, AttendanceRepository attendance, AttendancePolicyService policies,
            AttendanceCalendarProperties calendar, Clock clock,
            AttendanceDailyExceptionRepository exceptions) {
        this.users = users; this.assignments = assignments; this.mentorProfiles = mentorProfiles;
        this.internships = internships; this.attendance = attendance; this.policies = policies;
        this.calendar = calendar; this.clock = clock; this.exceptions = exceptions;
    }

    public StudentAttendanceDirectoryService(UserRepository users, MentorStudentAssignmentRepository assignments,
            MentorProfileRepository mentorProfiles,
            InternshipDetailsRepository internships, AttendanceRepository attendance, AttendancePolicyService policies,
            AttendanceCalendarProperties calendar, Clock clock) {
        this(users, assignments, mentorProfiles, internships, attendance, policies, calendar, clock, null);
    }

    public record Totals(long submittedDays, long presentDays, long absentDays, long leaveDays, long halfDays,
            long holidays, long workingDays, long pendingDays, long totalDays, BigDecimal percentage,
            long approvedHolidayDays, long approvedExtraWorkingDays) {
        public Totals(long submittedDays, long presentDays, long absentDays, long leaveDays, long halfDays,
                long holidays, long workingDays, long pendingDays, long totalDays, BigDecimal percentage) {
            this(submittedDays, presentDays, absentDays, leaveDays, halfDays, holidays, workingDays, pendingDays, totalDays, percentage, 0, 0);
        }
        public Totals(long presentDays, long absentDays, long totalDays, BigDecimal percentage) {
            this(presentDays, presentDays, absentDays, 0, 0, 0, totalDays, 0, totalDays, percentage, 0, 0);
        }
    }
    public record ReviewTotals(long submitted, long gpsVerified, long pending, long approved, long rejected, long manualReview) {}
    public record StudentRow(Long studentUserId, Long internshipId, Integer internshipNumber, String uid, String studentName, String branch,
            String facultyMentor, String company, String workflowStatus, Totals attendance, ReviewTotals reviewSummary) {}
    public record InternshipSummary(Long id, int internshipNumber, String companyName, String internshipMode) {}
    public record History(Long studentUserId, String attendanceZone, Totals totals, List<AttendanceResponse> records,
            List<InternshipSummary> internships, Long selectedInternshipId) {}

    public List<StudentRow> students(UserPrincipal principal) {
        requireRole(principal);
        List<MentorStudentAssignment> current = principal.role().equals("ADMIN") ? assignments.findAllByActiveTrue()
                : assignments.findAllByMentorIdAndActiveTrueOrderByStudentEmailAsc(principal.id());
        // Start from accounts/active assignments, never from a one-to-many attendance join.
        Map<Long, User> unique = new LinkedHashMap<>();
        if (principal.role().equals("ADMIN")) users.findAllByRoleOrderByEmailAsc(Role.STUDENT)
                .stream().filter(User::isActive).forEach(student -> unique.put(student.getId(), student));
        else current.stream().filter(a -> a.getStudent().isActive()).forEach(assignment -> unique.put(assignment.getStudent().getId(), assignment.getStudent()));
        if (unique.isEmpty()) return List.of();
        Map<Long, String> mentorNames = mentorProfiles.findAllByOrderByNameAsc().stream()
                .filter(p -> p.getUser() != null && p.getName() != null && !p.getName().isBlank())
                .collect(Collectors.toMap(p -> p.getUser().getId(), p -> p.getName().trim(), (a, b) -> a));
        Map<Long, String> mentors = new HashMap<>();
        current.forEach(a -> {
            String name = mentorNames.get(a.getMentor().getId());
            mentors.put(a.getStudent().getId(), name != null && !name.isBlank() ? name : null);
        });
        Map<Long, List<InternshipDetails>> details = internships.findAllByOnboardingStudentIdIn(unique.keySet()).stream()
                .collect(Collectors.groupingBy(i -> i.getOnboarding().getStudent().getId()));
        Map<Long, List<AttendanceRecord>> records = attendance.findAllByStudentIdInOrderByAttendanceDateAscIdAsc(unique.keySet())
                .stream().collect(Collectors.groupingBy(r -> r.getStudent().getId()));
        return unique.values().stream().flatMap(student -> {
            var profile = student.getStudentProfile(); var account = student.getStudentAccount();
            String authoritativeUid = profile != null && profile.getUid() != null && !profile.getUid().isBlank()
                    ? profile.getUid().trim()
                    : (account != null && account.getUid() != null && !account.getUid().isBlank() && !account.getUid().contains("@")
                        ? account.getUid().trim() : null);
            List<InternshipDetails> owned=details.getOrDefault(student.getId(),List.of()).stream()
                    .sorted(Comparator.comparing(i->i.getOnboarding().getInternshipNumber())).toList();
            if(owned.isEmpty()) return java.util.stream.Stream.of(new StudentRow(student.getId(),null,null,authoritativeUid,
                    profile != null ? profile.getStudentName() : account == null ? null : account.getStudentName(),
                    profile == null ? null : profile.getBranch().getLabel(), mentors.get(student.getId()),
                    null,"NOT_SECURED",totals(List.of()),reviewTotals(List.of())));
            return owned.stream().map(internship -> {
                List<AttendanceRecord> scoped=records.getOrDefault(student.getId(),List.of()).stream()
                        .filter(r->r.getInternship()!=null && r.getInternship().getId().equals(internship.getId())).toList();
                return new StudentRow(student.getId(),internship.getId(),(int)internship.getOnboarding().getInternshipNumber(),authoritativeUid,
                        profile != null ? profile.getStudentName() : account == null ? null : account.getStudentName(),
                        profile == null ? null : profile.getBranch().getLabel(),mentors.get(student.getId()),internship.getCompanyName(),
                        internship.getOnboarding().getWorkflowStatus().name(),totals(scoped,internship),reviewTotals(scoped));
            });
        }).toList();
    }

    public History history(UserPrincipal principal, Long studentId) {
        return history(principal, studentId, null);
    }

    public History history(UserPrincipal principal, Long studentId, Long requestedInternshipId) {
        if (principal != null && principal.role().equals("STUDENT")) {
            if (!principal.id().equals(studentId)) {
                throw new AttendanceException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You can only view your own attendance history");
            }
        } else {
            requireRole(principal);
            if (principal != null && principal.role().equals("MENTOR") && assignments.findByStudentIdAndActiveTrue(studentId)
                    .filter(a -> a.getMentor().getId().equals(principal.id())).isEmpty())
                throw new AttendanceException(HttpStatus.FORBIDDEN, "STUDENT_NOT_ASSIGNED", "This student is not assigned to you");
        }
        users.findById(studentId).filter(u -> u.getRole() == Role.STUDENT).orElseThrow(() ->
                new AttendanceException(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "Student not found"));

        List<InternshipDetails> studentInternships = internships.findAllByOnboardingStudentIdOrderByOnboardingInternshipNumber(studentId);
        List<InternshipSummary> internshipSummaries = studentInternships.stream()
                .map(i -> new InternshipSummary(i.getId(), i.getOnboarding().getInternshipNumber(), i.getCompanyName(),
                        i.getInternshipMode() == null ? null : i.getInternshipMode().name()))
                .toList();

        InternshipDetails selectedInternship = null;
        if (requestedInternshipId != null) {
            selectedInternship = studentInternships.stream()
                    .filter(i -> i.getId().equals(requestedInternshipId))
                    .findFirst()
                    .orElseThrow(() -> new AttendanceException(HttpStatus.NOT_FOUND, "INTERNSHIP_NOT_FOUND", "Internship was not found for this student"));
        } else if (!studentInternships.isEmpty()) {
            selectedInternship = studentInternships.get(0);
        }

        List<AttendanceRecord> records;
        if (selectedInternship != null) {
            records = attendance.findAllByStudentIdAndInternshipIdOrderByAttendanceDateAscIdAsc(studentId, selectedInternship.getId());
        } else {
            records = attendance.findAllByStudentIdInOrderByAttendanceDateAscIdAsc(List.of(studentId));
        }

        Long selectedId = selectedInternship != null ? selectedInternship.getId() : null;
        return new History(studentId, clock.getZone().getId(), totals(records, selectedInternship),
                records.stream().map(AttendanceService::response).toList(), internshipSummaries, selectedId);
    }

    public Totals totals(List<AttendanceRecord> records) {
        return totals(records, null);
    }

    public Totals totals(List<AttendanceRecord> records, InternshipDetails fallbackInternship) {
        LocalDate today = edu.institution.ims.internship.InternshipLifecycle.today(clock);
        InternshipDetails internship = fallbackInternship;
        if (internship == null) {
            for (AttendanceRecord r : records) {
                if (r.getInternship() != null) {
                    internship = r.getInternship();
                    break;
                }
            }
        }

        // 1. Student actual submissions (non-automatic, non-deleted, within date bounds)
        Set<LocalDate> submittedDates = new HashSet<>();
        for (AttendanceRecord record : records) {
            if (record.getDeletedAt() != null || record.isAutomaticAbsent()) continue;
            var curInternship = record.getInternship() != null ? record.getInternship() : internship;
            LocalDate date = record.getAttendanceDate();
            if (curInternship != null && curInternship.getStartDate() != null && date.isBefore(curInternship.getStartDate())) continue;
            if (curInternship != null && curInternship.getEndDate() != null && date.isAfter(curInternship.getEndDate())) continue;
            if (date.isAfter(today)) continue;
            submittedDates.add(date);
        }
        long submittedDays = submittedDates.size();

        // 2. Scheduled working days & calendar counts up to today
        long workingDays = 0;
        long holidays = 0;
        long leaveDays = 0;
        long approvedHolidayDays = 0;
        long approvedExtraWorkingDays = 0;
        if (internship != null && internship.getStartDate() != null && internship.getEndDate() != null
                && !today.isBefore(internship.getStartDate())) {
            LocalDate windowEnd = internship.getEndDate().isBefore(today) ? internship.getEndDate() : today;
            Long studentId = records.isEmpty() ? null : records.get(0).getStudent() != null ? records.get(0).getStudent().getId() : null;
            for (LocalDate d = internship.getStartDate(); !d.isAfter(windowEnd); d = d.plusDays(1)) {
                boolean workingDay = isWorkingDay(internship, d);
                boolean holiday = calendar.getHolidays().contains(d);
                boolean leave = studentId != null && calendar.isExcluded(studentId, d);
                boolean approvedHoliday = exceptions != null && exceptions.existsByInternshipIdAndExceptionDateAndExceptionTypeAndStatus(
                        internship.getId(), d, AttendanceExceptionType.COMPANY_HOLIDAY, AttendanceExceptionStatus.APPROVED);
                boolean approvedExtra = exceptions != null && exceptions.existsByInternshipIdAndExceptionDateAndExceptionTypeAndStatus(
                        internship.getId(), d, AttendanceExceptionType.EXTRA_WORKDAY, AttendanceExceptionStatus.APPROVED);

                if (approvedHoliday) approvedHolidayDays++;
                if (holiday || approvedHoliday) holidays++;
                if (leave) leaveDays++;
                if (approvedExtra) approvedExtraWorkingDays++;

                if (workingDay && !holiday && !leave && !approvedHoliday && policies.resolve(internship, d).configurationReady()) {
                    workingDays++;
                }
            }
        }

        // 3. Status mapping on applicable working days
        Map<LocalDate, AttendanceResult> days = new HashMap<>();
        Set<LocalDate> pendingDates = new HashSet<>();
        Set<LocalDate> extraWorkDatesWithPresent = new HashSet<>();
        for (AttendanceRecord record : records) {
            if (record.getDeletedAt() != null) continue;
            LocalDate date = record.getAttendanceDate();
            var curInternship = record.getInternship() != null ? record.getInternship() : internship;
            if (curInternship == null) continue;
            if (date.isAfter(today) || (curInternship.getStartDate() != null && date.isBefore(curInternship.getStartDate()))
                    || (curInternship.getEndDate() != null && date.isAfter(curInternship.getEndDate()))) continue;

            // If date is an approved holiday, it must not be PRESENT or ABSENT
            boolean approvedHoliday = exceptions != null && exceptions.existsByInternshipIdAndExceptionDateAndExceptionTypeAndStatus(
                    curInternship.getId(), date, AttendanceExceptionType.COMPANY_HOLIDAY, AttendanceExceptionStatus.APPROVED);
            if (approvedHoliday) continue;

            // Exclude if institutional student leave
            if (calendar.isExcluded(record.getStudent().getId(), date) || !policies.resolve(curInternship, date).configurationReady()) continue;

            boolean isScheduledWorkingDay = isWorkingDay(curInternship, date);
            boolean isApprovedExtraWork = exceptions != null && exceptions.existsByInternshipIdAndExceptionDateAndExceptionTypeAndStatus(
                    curInternship.getId(), date, AttendanceExceptionType.EXTRA_WORKDAY, AttendanceExceptionStatus.APPROVED);

            if (record.getAttendanceResult() == AttendanceResult.PENDING_MENTOR_REVIEW) {
                pendingDates.add(date);
                continue;
            }
            if (record.getAttendanceResult() != AttendanceResult.PRESENT && record.getAttendanceResult() != AttendanceResult.ABSENT) continue;

            if (record.getAttendanceResult() == AttendanceResult.PRESENT) {
                // Valid finalized PRESENT record counts on scheduled working days or approved extra workdays
                if (isScheduledWorkingDay || isApprovedExtraWork) {
                    days.merge(date, AttendanceResult.PRESENT, (a, b) -> AttendanceResult.PRESENT);
                    if (!isScheduledWorkingDay) {
                        extraWorkDatesWithPresent.add(date);
                    }
                }
            } else if (record.getAttendanceResult() == AttendanceResult.ABSENT || record.isAutomaticAbsent()) {
                // Absent is only counted on scheduled working days (never on scheduled days off)
                if (isScheduledWorkingDay) {
                    days.merge(date, AttendanceResult.ABSENT, (a, b) -> a == AttendanceResult.PRESENT || b == AttendanceResult.PRESENT
                            ? AttendanceResult.PRESENT : AttendanceResult.ABSENT);
                }
            }
        }
        long pendingDays = pendingDates.size();
        long present = days.values().stream().filter(r -> r == AttendanceResult.PRESENT).count();
        long absent = days.values().stream().filter(r -> r == AttendanceResult.ABSENT).count();
        long total = present + absent;
        long totalExtraWorkingDays = Math.max(approvedExtraWorkingDays, (long) extraWorkDatesWithPresent.size());
        return new Totals(submittedDays, present, absent, leaveDays, 0, holidays, workingDays, pendingDays, total,
                total == 0 ? null : BigDecimal.valueOf(present * 100).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP),
                approvedHolidayDays, totalExtraWorkingDays);
    }

    private static boolean isWorkingDay(InternshipDetails internship, LocalDate date) {
        if (internship == null || date == null) return false;
        if (internship.getWorkingDays() == null) {
            return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
        }
        return internship.isWorkingDay(date);
    }

    private ReviewTotals reviewTotals(List<AttendanceRecord> records) {
        var submitted = records.stream().filter(r -> !r.isAutomaticAbsent() && r.getDeletedAt() == null).toList();
        return new ReviewTotals(submitted.size(),
                submitted.stream().filter(r -> r.getEvidence() != null && r.getEvidence().getLocationVerificationStatus() == LocationVerificationStatus.VERIFIED).count(),
                submitted.stream().filter(r -> AttendanceReviewResponse.from(r).status() == AttendanceReviewStatus.PENDING).count(),
                submitted.stream().filter(r -> AttendanceReviewResponse.from(r).status() == AttendanceReviewStatus.APPROVED).count(),
                submitted.stream().filter(r -> AttendanceReviewResponse.from(r).status() == AttendanceReviewStatus.REJECTED).count(),
                submitted.stream().filter(r -> AttendanceReviewResponse.from(r).status() == AttendanceReviewStatus.MANUAL_REVIEW).count());
    }

    private void requireRole(UserPrincipal principal) {
        if (principal == null || !principal.active() || !(principal.role().equals("ADMIN") || principal.role().equals("MENTOR")))
            throw new AttendanceException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Admin or faculty mentor access required");
    }
}
