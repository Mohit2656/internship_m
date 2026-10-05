package edu.institution.ims.studentprofile;

import edu.institution.ims.common.PagedResponse;
import edu.institution.ims.dashboard.AttentionCategory;
import edu.institution.ims.studentprofile.dto.*;
import edu.institution.ims.internship.*;
import edu.institution.ims.user.*;
import edu.institution.ims.studentprofile.security.AadhaarEncryptionService;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminStudentService {
    private final UserRepository users; private final StudentProfileRepository profiles; private final InternshipOnboardingService onboardingService; private final InternshipDetailsService detailsService; private final AadhaarEncryptionService encryption;
    public AdminStudentService(UserRepository users, StudentProfileRepository profiles, InternshipOnboardingService onboardingService, InternshipDetailsService detailsService, AadhaarEncryptionService encryption) {
        this.users = users; this.profiles = profiles; this.onboardingService = onboardingService; this.detailsService = detailsService; this.encryption = encryption;
    }

    @Transactional(readOnly = true) public PagedResponse<AdminStudentSummaryResponse> list(String search, Branch branch, String academicYear, Integer semester,
            WorkflowStatus internshipStatus, InternshipSource internshipSource, ProfileStatus profileStatus, CompanyType companyType,
            InternshipMode internshipMode, Boolean paid, Boolean fullTimeEmploymentOffered, AttentionCategory attention, int page, int size) {
        int safePage = Math.max(page, 0); int safeSize = Math.min(Math.max(size, 1), 100);
        Specification<User> spec = (root, query, cb) -> {
            var profile = root.<User, StudentProfile>join("studentProfile", JoinType.LEFT);
            var onboarding = root.<User, InternshipOnboarding>join("internshipOnboarding", JoinType.LEFT);
            var details = onboarding.<InternshipOnboarding, InternshipDetails>join("internshipDetails", JoinType.LEFT);
            var predicate = cb.equal(root.get("role"), Role.STUDENT);
            if (search != null && !search.isBlank()) {
                String value = "%" + search.trim().toLowerCase() + "%";
                predicate = cb.and(predicate, cb.or(cb.like(cb.lower(profile.get("studentName")), value), cb.like(cb.lower(profile.get("uid")), value),
                        cb.like(cb.lower(profile.get("rollNumber")), value), cb.like(cb.lower(profile.get("instituteEmail")), value)));
            }
            if (branch != null) predicate = cb.and(predicate, cb.equal(profile.get("branch"), branch));
            if (academicYear != null && !academicYear.isBlank()) predicate = cb.and(predicate, cb.equal(profile.get("academicYear"), academicYear.trim()));
            if (semester != null) predicate = cb.and(predicate, cb.equal(profile.get("semester"), semester));
            if (profileStatus == ProfileStatus.COMPLETE) predicate = cb.and(predicate, cb.isNotNull(profile.get("id")));
            if (profileStatus == ProfileStatus.INCOMPLETE) predicate = cb.and(predicate, cb.isNull(profile.get("id")));
            if (internshipStatus == WorkflowStatus.NOT_SECURED) predicate = cb.and(predicate, cb.isNull(onboarding.get("id")));
            else if (internshipStatus != null) predicate = cb.and(predicate, cb.equal(onboarding.get("workflowStatus"), internshipStatus));
            if (internshipSource != null) predicate = cb.and(predicate, cb.equal(onboarding.get("source"), internshipSource));
            if (companyType != null) predicate = cb.and(predicate, cb.equal(details.get("companyType"), companyType));
            if (internshipMode != null) predicate = cb.and(predicate, cb.equal(details.get("internshipMode"), internshipMode));
            if (paid != null) predicate = cb.and(predicate, paid ? cb.greaterThan(details.get("stipendPerMonth"), java.math.BigDecimal.ZERO)
                    : cb.equal(details.get("stipendPerMonth"), java.math.BigDecimal.ZERO));
            if (fullTimeEmploymentOffered != null) predicate = cb.and(predicate, cb.equal(details.get("fullTimeEmploymentOffered"), fullTimeEmploymentOffered));
            if (attention != null) predicate = cb.and(predicate, attentionPredicate(attention, cb, profile, onboarding, details));
            query.distinct(true); return predicate;
        };
        return PagedResponse.from(users.findAll(spec, PageRequest.of(safePage, safeSize, Sort.by("email").ascending())).map(this::summary));
    }
    private static jakarta.persistence.criteria.Predicate attentionPredicate(AttentionCategory attention, jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Join<User, StudentProfile> profile, jakarta.persistence.criteria.Join<User, InternshipOnboarding> onboarding,
            jakarta.persistence.criteria.Join<InternshipOnboarding, InternshipDetails> details) {
        return switch (attention) {
            case PROFILE_INCOMPLETE -> cb.isNull(profile.get("id"));
            case INTERNSHIP_NOT_SECURED -> cb.isNull(onboarding.get("id"));
            case DOCUMENTS_PENDING -> onboarding.get("workflowStatus").in(WorkflowStatus.INTERNSHIP_SECURED, WorkflowStatus.DOCUMENTS_PENDING);
            case DIARY_PENDING -> cb.equal(onboarding.get("workflowStatus"), WorkflowStatus.DOCUMENTS_COMPLETE);
            case INTERNSHIP_DETAILS_PENDING -> cb.and(cb.equal(onboarding.get("workflowStatus"), WorkflowStatus.DIARY_ISSUED), cb.isNull(details.get("id")));
        };
    }
    @Transactional(readOnly = true) public AdminStudentDetailResponse detail(Long userId) {
        User user = users.findById(userId).filter(u -> u.getRole() == Role.STUDENT).orElseThrow(ProfileNotFoundException::new);
        StudentProfile profile = profiles.findByUserId(userId).orElse(null);
        return new AdminStudentDetailResponse(user.getId(), user.getEmail(), profile == null ? ProfileStatus.INCOMPLETE : ProfileStatus.COMPLETE,
                profile == null ? null : StudentProfileService.response(profile), profile == null ? null : encryption.decrypt(profile.getAadhaarEncrypted()),
                onboardingService.getForStudent(userId), detailsService.findForStudentOrNull(userId));
    }
    private AdminStudentSummaryResponse summary(User user) {
        StudentProfile p = user.getStudentProfile();
        InternshipOnboarding onboarding = user.getInternshipOnboarding();
        var internship = onboarding == null ? InternshipOnboardingService.notSecuredResponse() : onboardingService.response(onboarding);
        StudentAccount account = user.getStudentAccount();
        return new AdminStudentSummaryResponse(user.getId(), user.getEmail(), p == null ? account == null ? null : account.getStudentName() : p.getStudentName(), p == null ? account == null ? null : account.getUid() : p.getUid(),
                p == null ? null : p.getRollNumber(), p == null ? null : p.getBranch().name(), p == null ? null : p.getBranch().getLabel(),
                p == null ? null : p.getInstituteEmail(), p == null ? null : p.getPhoneNumber(), p == null ? null : p.getAcademicYear(),
                p == null ? account == null ? null : account.getSemester() : p.getSemester(), p == null ? ProfileStatus.INCOMPLETE : ProfileStatus.COMPLETE,
                internship.workflowStatus(), internship.source(), internship.sourceLabel(), internship.documentsCompleted(),
                internship.documentsRequired(), internship.diaryIssued());
    }
}
