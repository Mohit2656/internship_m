package edu.institution.ims.mentor;

import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;

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
        if (users.existsByEmailIgnoreCase(email)) throw conflict("DUPLICATE_EMAIL", "An account with this email already exists");
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

    @Transactional(readOnly = true)
    public List<MentorAccountResponse> list() { return mentors.findAllByOrderByNameAsc().stream().map(this::response).toList(); }

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
