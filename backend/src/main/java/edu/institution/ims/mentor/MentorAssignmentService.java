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
        List<User> students = users.findAllByRoleOrderByEmailAsc(Role.STUDENT);
        List<MentorStudentAssignment> activeAssignments = assignments.findAllByActiveTrue();
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
                profile == null ? account == null ? null : account.getSemester() : profile.getSemester(),
                profile == null ? ProfileStatus.INCOMPLETE : ProfileStatus.COMPLETE,
                mentorId, mentorId == null ? null : mentorNames.get(mentorId));
    }

    private static MentorAssignmentException error(HttpStatus status, String code, String message) {
        return new MentorAssignmentException(status, code, message);
    }
}
