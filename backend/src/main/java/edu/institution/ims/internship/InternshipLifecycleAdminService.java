package edu.institution.ims.internship;

import edu.institution.ims.internship.dto.InternshipDetailsResponse;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.Role;
import edu.institution.ims.user.User;
import edu.institution.ims.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Service
public class InternshipLifecycleAdminService {
    private final InternshipDetailsRepository internships;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public InternshipLifecycleAdminService(InternshipDetailsRepository internships,
                                           UserRepository users,
                                           JdbcTemplate jdbc,
                                           Clock clock) {
        this.internships = internships;
        this.users = users;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public InternshipDetailsResponse terminateEarly(UserPrincipal adminPrincipal, Long internshipId, String reason) {
        User admin = validateAdmin(adminPrincipal);
        if (reason == null || reason.trim().length() < 5 || reason.trim().length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reason must be between 5 and 1000 characters");
        }

        InternshipDetails internship = internships.findById(internshipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Internship not found"));

        if (internship.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot terminate a deleted internship");
        }
        if (internship.isEarlyTerminated()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Internship has already been terminated early");
        }

        Instant now = clock.instant();
        LocalDate today = InternshipLifecycle.today(clock);

        internship.terminateEarly(admin, reason.trim(), now);
        internships.saveAndFlush(internship);

        // Record in audit table
        Long studentId = internship.getOnboarding().getStudent().getId();
        jdbc.update("insert into internship_lifecycle_audit (internship_id, student_user_id, action, actor_user_id, effective_date, reason, occurred_at) values (?,?,?,?,?,?,?)",
                internship.getId(), studentId, "TERMINATED_EARLY", admin.getId(), Date.valueOf(today), reason.trim(), Timestamp.from(now));

        return InternshipDetailsService.response(internship);
    }

    @Transactional
    public InternshipDetailsResponse softDelete(UserPrincipal adminPrincipal, Long internshipId, String reason) {
        User admin = validateAdmin(adminPrincipal);
        InternshipDetails internship = internships.findById(internshipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Internship not found"));

        if (internship.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Internship is already deleted");
        }

        Instant now = clock.instant();
        LocalDate today = InternshipLifecycle.today(clock);

        internship.softDelete(admin, reason != null ? reason.trim() : "Deleted by Admin", now);
        internships.saveAndFlush(internship);

        // Record in audit table
        Long studentId = internship.getOnboarding().getStudent().getId();
        jdbc.update("insert into internship_lifecycle_audit (internship_id, student_user_id, action, actor_user_id, effective_date, reason, occurred_at) values (?,?,?,?,?,?,?)",
                internship.getId(), studentId, "SOFT_DELETE", admin.getId(), Date.valueOf(today), reason != null ? reason.trim() : "Deleted by Admin", Timestamp.from(now));

        return InternshipDetailsService.response(internship);
    }

    private User validateAdmin(UserPrincipal adminPrincipal) {
        if (adminPrincipal == null || !"ADMIN".equals(adminPrincipal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only III Cell Incharge / Admin can perform this action");
        }
        return users.findById(adminPrincipal.id())
                .filter(u -> u.isActive() && u.getRole() == Role.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin user record not found or inactive"));
    }
}
