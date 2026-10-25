package edu.institution.ims.mentor;

import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class MentorAccountService {
    private final UserRepository users; private final MentorProfileRepository mentors;
    private final MentorStudentAssignmentRepository assignments; private final PasswordEncoder encoder;
    public MentorAccountService(UserRepository users, MentorProfileRepository mentors,
            MentorStudentAssignmentRepository assignments, PasswordEncoder encoder) {
        this.users = users; this.mentors = mentors; this.assignments = assignments; this.encoder = encoder;
    }

    @Transactional
    public MentorAccountResponse create(MentorAccountRequest request) {
        String email = email(request.instituteEmail()); String employeeId = employeeId(request.employeeId());
        Optional<User> existing = users.findByEmailForPasswordUpdate(email);
        if (existing.isPresent()) return recreate(request, existing.get(), employeeId);
        if (mentors.existsByEmployeeIdIgnoreCase(employeeId)) throw conflict("DUPLICATE_EMPLOYEE_ID", "Employee ID is already assigned to another mentor");
        try {
            User user = users.saveAndFlush(new User(email, encoder.encode(employeeId), Role.MENTOR, true));
            user.requirePasswordChange(); users.saveAndFlush(user);
            MentorProfile profile = mentors.saveAndFlush(new MentorProfile(user, clean(request.name()), employeeId,
                    clean(request.department()), clean(request.designation()), clean(request.phone())));
            return response(profile);
        } catch (DataIntegrityViolationException e) {
            throw conflict("MENTOR_ACCOUNT_CONFLICT", "Email or employee ID is already assigned to another account");
        }
    }

    private MentorAccountResponse recreate(MentorAccountRequest request, User user, String employeeId) {
        if (user.getRole() != Role.MENTOR || user.isActive()) {
            throw conflict("DUPLICATE_EMAIL", "An account with this email already exists");
        }
        MentorProfile profile = mentors.findByUserId(user.getId()).orElseThrow(() ->
                conflict("MENTOR_IDENTITY_CONFLICT", "The retained mentor profile could not be verified"));
        if (!profile.getEmployeeId().equalsIgnoreCase(employeeId) || !profile.getName().equals(clean(request.name()))) {
            throw conflict("MENTOR_IDENTITY_CONFLICT", "To create this mentor account again, use the same name, institute email and Employee ID as the retained account");
        }
        // Reuse the retained identity so student assignments and historical reviewer/actor links never change.
        user.recreateMentorAccount(encoder.encode(employeeId));
        profile.updateAccountDetails(clean(request.department()), clean(request.designation()), clean(request.phone()));
        users.saveAndFlush(user);
        mentors.saveAndFlush(profile);
        return response(profile);
    }

    @Transactional(readOnly = true)
    public List<MentorAccountResponse> list() {
        return mentors.findAllByOrderByNameAsc().stream().filter(profile -> profile.getUser().isActive()).map(this::response).toList();
    }

    @Transactional
    public MentorAccountResponse deactivate(Long userId, UserPrincipal principal) {
        if (principal == null || !principal.isEnabled() || !Role.ADMIN.name().equals(principal.role())) {
            throw new AccessDeniedException("Only an active IIIC Coordinator may delete mentor accounts");
        }
        users.findById(principal.id())
                .filter(user -> user.isActive() && user.getRole() == Role.ADMIN && user.getTokenVersion() == principal.tokenVersion())
                .orElseThrow(() -> new AccessDeniedException("Only an active IIIC Coordinator may delete mentor accounts"));
        if (principal.id().equals(userId)) {
            throw new MentorAssignmentException(HttpStatus.BAD_REQUEST, "PROTECTED_ACCOUNT", "Your own account cannot be deleted");
        }
        User target = users.findByIdForPasswordUpdate(userId)
                .orElseThrow(() -> new MentorAssignmentException(HttpStatus.NOT_FOUND, "MENTOR_NOT_FOUND", "Mentor account was not found"));
        if (target.getRole() != Role.MENTOR) {
            throw new MentorAssignmentException(HttpStatus.BAD_REQUEST, "INVALID_MENTOR_ACCOUNT", "Only faculty mentor accounts may be deleted; administrative and student accounts are protected");
        }
        MentorProfile profile = mentors.findByUserId(userId)
                .orElseThrow(() -> new MentorAssignmentException(HttpStatus.NOT_FOUND, "MENTOR_NOT_FOUND", "Mentor profile was not found"));
        // Retain identity and every assignment/review/audit reference, including accounts without current students.
        target.deactivate();
        users.saveAndFlush(target);
        return response(profile);
    }

    @Transactional(readOnly = true)
    public MentorAccountResponse own(UserPrincipal principal) {
        return mentors.findByUserId(principal.id()).map(this::response)
                .orElseThrow(() -> new IllegalArgumentException("Mentor profile was not found"));
    }

    MentorAccountResponse response(MentorProfile profile) {
        User user = profile.getUser();
        return new MentorAccountResponse(profile.getId(), user.getId(), profile.getName(), profile.getEmployeeId(), user.getEmail(),
                profile.getDepartment(), profile.getDesignation(), profile.getPhone(), user.isActive(), user.isMustChangePassword(),
                assignments.countByMentorIdAndActiveTrue(user.getId()), profile.getCreatedAt());
    }
    private static MentorAccountConflictException conflict(String code, String message) { return new MentorAccountConflictException(code, message); }
    private static String clean(String value) { return value.trim(); }
    private static String email(String value) { return clean(value).toLowerCase(Locale.ROOT); }
    private static String employeeId(String value) { return clean(value).toUpperCase(Locale.ROOT); }
}
