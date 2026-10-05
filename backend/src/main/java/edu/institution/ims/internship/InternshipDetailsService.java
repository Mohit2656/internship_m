package edu.institution.ims.internship;

import edu.institution.ims.internship.dto.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.studentprofile.StudentProfileRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternshipDetailsService {
    private final InternshipDetailsRepository details;
    private final InternshipOnboardingRepository onboardings;
    private final StudentProfileRepository profiles;
    public InternshipDetailsService(InternshipDetailsRepository details, InternshipOnboardingRepository onboardings, StudentProfileRepository profiles) {
        this.details = details; this.onboardings = onboardings; this.profiles = profiles;
    }

    @Transactional(readOnly = true) public InternshipDetailsResponse getOwn(UserPrincipal principal) {
        return response(details.findByOnboardingStudentId(principal.id()).orElseThrow(InternshipDetailsService::notFound));
    }

    @Transactional public InternshipDetailsResponse createOwn(UserPrincipal principal, InternshipDetailsRequest request) {
        if (!profiles.existsByUserId(principal.id())) throw conflict("PROFILE_INCOMPLETE", "Complete your student profile before entering internship details");
        InternshipOnboarding onboarding = onboarding(principal.id());
        if (details.existsByOnboardingId(onboarding.getId())) throw conflict("INTERNSHIP_DETAILS_ALREADY_EXIST", "Internship details already exist for this onboarding record");
        if (onboarding.getWorkflowStatus() != WorkflowStatus.DIARY_ISSUED)
            throw conflict("DIARY_REQUIRED", "Internship details can be entered only after the internship diary is issued");
        validate(request);
        InternshipDetails entity = new InternshipDetails(onboarding); apply(entity, request);
        try { details.saveAndFlush(entity); }
        catch (DataIntegrityViolationException e) { throw conflict("INTERNSHIP_DETAILS_ALREADY_EXIST", "Internship details already exist for this onboarding record"); }
        onboarding.markInternshipDetailsComplete(); onboardings.saveAndFlush(onboarding);
        return response(entity);
    }

    @Transactional public InternshipDetailsResponse updateOwn(UserPrincipal principal, InternshipDetailsRequest request) {
        InternshipDetails entity = details.findByOnboardingStudentId(principal.id()).orElseThrow(InternshipDetailsService::notFound);
        WorkflowStatus status = entity.getOnboarding().getWorkflowStatus();
        if (status != WorkflowStatus.DIARY_ISSUED && status != WorkflowStatus.INTERNSHIP_DETAILS_COMPLETE)
            throw conflict("INVALID_WORKFLOW_STATE", "Internship details cannot be updated in the current workflow state");
        validate(request); apply(entity, request); entity.getOnboarding().markInternshipDetailsComplete();
        return response(details.saveAndFlush(entity));
    }

    @Transactional(readOnly = true) public InternshipDetailsResponse getForAdmin(Long studentId) {
        return response(details.findByOnboardingStudentId(studentId).orElseThrow(InternshipDetailsService::notFound));
    }

    @Transactional(readOnly = true) public InternshipDetailsResponse findForStudentOrNull(Long studentId) {
        return details.findByOnboardingStudentId(studentId).map(InternshipDetailsService::response).orElse(null);
    }

    private InternshipOnboarding onboarding(Long studentId) {
        return onboardings.findByStudentId(studentId).orElseThrow(() -> conflict("ONBOARDING_NOT_STARTED", "Complete internship onboarding before entering internship details"));
    }
    private static void validate(InternshipDetailsRequest request) {
        if (!request.endDate().isAfter(request.startDate()))
            throw bad("INVALID_DATE_RANGE", "End date must be after start date");
        String other = clean(request.otherCompanyType());
        if (request.companyType() == CompanyType.OTHER && other == null)
            throw bad("OTHER_COMPANY_TYPE_REQUIRED", "Describe the company type when Other is selected");
    }
    private static void apply(InternshipDetails entity, InternshipDetailsRequest request) {
        entity.apply(clean(request.companyName()), request.companyType(), request.companyType() == CompanyType.OTHER ? clean(request.otherCompanyType()) : null,
                clean(request.companyAddress()), clean(request.companyPhone()), clean(request.industrySupervisorName()), clean(request.industrySupervisorDesignation()),
                clean(request.industrySupervisorContact()), request.durationMonths(), request.startDate(), request.endDate(), request.totalWeeks(),
                request.internshipMode(), request.stipendPerMonth(), request.fullTimeEmploymentOffered());
    }
    public static InternshipDetailsResponse response(InternshipDetails entity) {
        return new InternshipDetailsResponse(entity.getId(), entity.getOnboarding().getId(), entity.getCompanyName(), entity.getCompanyType().name(),
                entity.getCompanyType().getLabel(), entity.getOtherCompanyType(), entity.getCompanyAddress(), entity.getCompanyPhone(),
                entity.getIndustrySupervisorName(), entity.getIndustrySupervisorDesignation(), entity.getIndustrySupervisorContact(),
                entity.getDurationMonths(), entity.getStartDate(), entity.getEndDate(), entity.getTotalWeeks(), entity.getInternshipMode().name(),
                entity.getInternshipMode().getLabel(), entity.getStipendPerMonth(), entity.isFullTimeEmploymentOffered(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
    private static String clean(String value) { if (value == null) return null; String clean = value.trim(); return clean.isBlank() ? null : clean; }
    private static InternshipDetailsException bad(String code, String message) { return new InternshipDetailsException(HttpStatus.BAD_REQUEST, code, message); }
    private static InternshipDetailsException conflict(String code, String message) { return new InternshipDetailsException(HttpStatus.CONFLICT, code, message); }
    private static InternshipDetailsException notFound() { return new InternshipDetailsException(HttpStatus.NOT_FOUND, "INTERNSHIP_DETAILS_NOT_FOUND", "Internship details were not found"); }
}
