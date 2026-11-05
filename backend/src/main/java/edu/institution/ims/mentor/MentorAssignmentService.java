package edu.institution.ims.mentor;

import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.studentprofile.ProfileStatus;
import edu.institution.ims.studentprofile.StudentProfile;
import edu.institution.ims.user.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MentorAssignmentService {
    private final UserRepository users;
    private final MentorProfileRepository mentors;
    private final MentorStudentAssignmentRepository assignments;
    private final MentorAccountService mentorAccounts;

    public MentorAssignmentService(UserRepository users, MentorProfileRepository mentors,
            MentorStudentAssignmentRepository assignments, MentorAccountService mentorAccounts) {
        this.users = users;
        this.mentors = mentors;
        this.assignments = assignments;
        this.mentorAccounts = mentorAccounts;
    }

    @Transactional(readOnly = true)
    public MentorAssignmentWorkspaceResponse workspace(Long mentorUserId) {
        MentorProfile mentor = requireMentor(mentorUserId);
        List<User> students = users.findAllByRoleOrderByEmailAsc(Role.STUDENT).stream().filter(User::isActive).toList();
        List<MentorStudentAssignment> activeAssignments = assignments.findAllByActiveTrue().stream().filter(a->a.getStudent().isActive()).toList();
        Map<Long, MentorStudentAssignment> byStudent = activeAssignments.stream()
                .collect(Collectors.toMap(a -> a.getStudent().getId(), Function.identity()));
        Map<Long, String> mentorNames = mentors.findAllByOrderByNameAsc().stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), MentorProfile::getName));

        List<AssignmentStudentResponse> assigned = new ArrayList<>();
        List<AssignmentStudentResponse> available = new ArrayList<>();
        for (User student : students) {
            MentorStudentAssignment current = byStudent.get(student.getId());
            AssignmentStudentResponse response = studentResponse(student, current, mentorNames);
            if (current != null && current.getMentor().getId().equals(mentorUserId)) assigned.add(response);
            else available.add(response);
        }
        long assignedCount = activeAssignments.size();
        return new MentorAssignmentWorkspaceResponse(mentorAccounts.response(mentor), assigned, available,
                students.size(), assignedCount, students.size() - assignedCount);
    }

    @Transactional
    public MentorAssignmentWorkspaceResponse assign(Long mentorUserId, AssignStudentsRequest request, UserPrincipal principal) {
        User account = users.findByIdForPasswordUpdate(mentorUserId)
                .filter(user -> user.getRole() == Role.MENTOR)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "MENTOR_NOT_FOUND", "Mentor was not found"));
        if (!account.isActive()) {
            throw error(HttpStatus.CONFLICT, "MENTOR_ACCOUNT_INACTIVE", "Students cannot be assigned to a deactivated mentor account");
        }
        MentorProfile mentorProfile = requireMentor(mentorUserId);
        User admin = requireRole(principal.id(), Role.ADMIN, "Assignment owner is not an ADMIN");
        List<Long> requestedIds = request.studentUserIds();
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(requestedIds);
        if (uniqueIds.size() != requestedIds.size()) {
            throw error(HttpStatus.BAD_REQUEST, "DUPLICATE_STUDENT_IDS", "Student IDs must not be repeated");
        }

        List<User> students = users.findAllByIdForAssignmentUpdate(uniqueIds);
        if (students.size() != uniqueIds.size()) {
            throw error(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "One or more students were not found");
        }
        if (students.stream().anyMatch(user -> user.getRole() != Role.STUDENT)) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_STUDENT_ROLE", "Only STUDENT users may be assigned to a mentor");
        }

        if(students.stream().anyMatch(user -> !user.isActive())) throw error(HttpStatus.CONFLICT,"STUDENT_ACCOUNT_INACTIVE","Deactivated students cannot receive new mentor assignments");
        Instant now = Instant.now();
        List<User> toAssign = new ArrayList<>();
        for (User student : students) {
            MentorStudentAssignment current = assignments.findByStudentIdAndActiveTrue(student.getId()).orElse(null);
            if (current != null && current.getMentor().getId().equals(mentorUserId)) continue;
            if (current != null) current.end(now);
            toAssign.add(student);
        }
        assignments.flush();
        User mentor = mentorProfile.getUser();
        assignments.saveAll(toAssign.stream()
                .map(student -> new MentorStudentAssignment(mentor, student, admin, now))
                .toList());
        assignments.flush();
        return workspace(mentorUserId);
    }

    @Transactional
    public MentorAssignmentWorkspaceResponse remove(Long mentorUserId, Long studentUserId, UserPrincipal principal) {
        requireMentor(mentorUserId);
        requireRole(principal.id(), Role.ADMIN, "Assignment owner is not an ADMIN");
        List<User> locked = users.findAllByIdForAssignmentUpdate(List.of(studentUserId));
        if (locked.isEmpty() || locked.get(0).getRole() != Role.STUDENT) {
            throw error(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "Student was not found");
        }
        MentorStudentAssignment current = assignments.findByStudentIdAndActiveTrue(studentUserId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "ACTIVE_ASSIGNMENT_NOT_FOUND", "Student is not assigned to this mentor"));
        if (!current.getMentor().getId().equals(mentorUserId)) {
            throw error(HttpStatus.CONFLICT, "ASSIGNMENT_MENTOR_MISMATCH", "Student is assigned to a different mentor");
        }
        current.end(Instant.now());
        assignments.flush();
        return workspace(mentorUserId);
    }

    @Transactional(readOnly = true)
    public List<AssignmentStudentResponse> assignedTo(UserPrincipal principal) {
        requireMentor(principal.id());
        return assignments.findAllByMentorIdAndActiveTrueOrderByStudentEmailAsc(principal.id()).stream()
                .filter(assignment -> assignment.getStudent().isActive())
                .map(assignment -> studentResponse(assignment.getStudent(), assignment, Map.of()))
                .toList();
    }

    private MentorProfile requireMentor(Long userId) {
        User user = requireRole(userId, Role.MENTOR, "Mentor was not found");
        return mentors.findByUserId(user.getId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "MENTOR_NOT_FOUND", "Mentor was not found"));
    }

    private User requireRole(Long userId, Role role, String message) {
        return users.findById(userId).filter(user -> user.getRole() == role)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, role.name() + "_NOT_FOUND", message));
    }

    private static AssignmentStudentResponse studentResponse(User student, MentorStudentAssignment current,
            Map<Long, String> mentorNames) {
        StudentProfile profile = student.getStudentProfile();
        StudentAccount account = student.getStudentAccount();
        Long mentorId = current == null ? null : current.getMentor().getId();
        return new AssignmentStudentResponse(student.getId(),
                profile == null ? account == null ? null : account.getStudentName() : profile.getStudentName(),
                profile == null ? account == null ? null : account.getUid() : profile.getUid(),
                profile == null ? null : profile.getBranch().name(),
                profile == null ? null : profile.getBranch().getLabel(),
                profile == null ? account == null ? null : account.getSemester() : Integer.valueOf(profile.getSemester()),
                profile == null ? ProfileStatus.INCOMPLETE : ProfileStatus.COMPLETE,
                mentorId, mentorId == null ? null : mentorNames.get(mentorId));
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Transactional
    public void ensureInternship2InheritsMentor(Long studentId) {
        if (studentId == null) return;
        if (assignments.findByStudentIdAndActiveTrue(studentId).isPresent()) {
            return;
        }
        User student = users.findById(studentId).filter(u -> u.getRole() == Role.STUDENT && u.isActive()).orElse(null);
        if (student == null || jdbc == null) return;

        try {
            Long reviewerId = jdbc.query(
                    "select l.mentor_approved_by from internship_details d "
                    + "join internship_onboarding o on o.id=d.onboarding_id and o.internship_number=1 "
                    + "join internship_locations l on l.internship_id=d.id "
                    + "where o.student_user_id=? and l.mentor_approved_by is not null",
                    (rs, n) -> rs.getLong(1), studentId).stream().findFirst().orElse(null);
            if (reviewerId != null) {
                User mentor = users.findById(reviewerId).filter(u -> u.isActive() && u.getRole() == Role.MENTOR).orElse(null);
                if (mentor != null) {
                    assignments.saveAndFlush(new MentorStudentAssignment(mentor, student, mentor, Instant.now()));
                    return;
                }
            }
        } catch (Exception ignored) {}

        try {
            String mentorName = jdbc.query("select faculty_mentor_name from student_profiles where user_id=?",
                    (rs, n) -> rs.getString(1), studentId).stream().findFirst().orElse(null);
            if (mentorName != null && !mentorName.isBlank()) {
                var mentorProfile = mentors.findAllByOrderByNameAsc().stream()
                        .filter(p -> p.getName() != null && p.getName().trim().equalsIgnoreCase(mentorName.trim()))
                        .findFirst().orElse(null);
                if (mentorProfile != null && mentorProfile.getUser() != null && mentorProfile.getUser().isActive()
                        && mentorProfile.getUser().getRole() == Role.MENTOR) {
                    assignments.saveAndFlush(new MentorStudentAssignment(mentorProfile.getUser(), student, mentorProfile.getUser(), Instant.now()));
                }
            }
        } catch (Exception ignored) {}
    }

    @jakarta.annotation.PostConstruct
    @Transactional
    public void backfillMissingInternship2Mentors() {
        if (jdbc == null) return;
        try {
            List<Long> studentIds = jdbc.query(
                    "select distinct o.student_user_id from internship_onboarding o "
                    + "where o.internship_number = 2 and not exists ("
                    + "  select 1 from mentor_student_assignments a where a.student_user_id = o.student_user_id and a.active = true"
                    + ")",
                    (rs, n) -> rs.getLong(1));
            for (Long sid : studentIds) {
                ensureInternship2InheritsMentor(sid);
            }
        } catch (Exception ignored) {}
    }

    private static MentorAssignmentException error(HttpStatus status, String code, String message) {
        return new MentorAssignmentException(status, code, message);
    }
}
