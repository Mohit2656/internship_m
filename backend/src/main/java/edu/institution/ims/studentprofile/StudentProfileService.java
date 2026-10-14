package edu.institution.ims.studentprofile;

import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.studentprofile.dto.*;
import edu.institution.ims.studentprofile.security.AadhaarEncryptionService;
import edu.institution.ims.user.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;

@Service
public class StudentProfileService {
    private final StudentProfileRepository profiles; private final UserRepository users; private final AadhaarEncryptionService encryption;
    public StudentProfileService(StudentProfileRepository profiles, UserRepository users, AadhaarEncryptionService encryption) { this.profiles = profiles; this.users = users; this.encryption = encryption; }

    @Transactional(readOnly = true) public StudentProfileResponse getOwn(UserPrincipal principal) {
        return response(profiles.findByUserId(principal.id()).orElseThrow(ProfileNotFoundException::new));
    }
    @Transactional(readOnly = true) public ProfilePrefillResponse getOwnPrefill(UserPrincipal principal) {
        User user = users.findById(principal.id()).filter(u -> u.getRole() == Role.STUDENT).orElseThrow(ProfileNotFoundException::new);
        StudentAccount account = user.getStudentAccount();
        StudentProfile profile = profiles.findByUserId(principal.id()).orElse(null);
        var readOnlyFields = new ArrayList<String>();
        readOnlyFields.add("instituteEmail"); readOnlyFields.add("branch");
        if (profile != null || hasValue(account == null ? null : account.getUid())) readOnlyFields.add("uid");
        if (hasValue(account == null ? null : account.getStudentName())) readOnlyFields.add("studentName");
        if (account != null && account.getSemester() != null) readOnlyFields.add("semester");
        Branch branch = profile == null ? Branch.COMPUTER_ENGINEERING : profile.getBranch();
        return new ProfilePrefillResponse(profile == null ? (account == null ? null : clean(account.getStudentName())) : profile.getStudentName(),
                profile == null ? (account == null ? null : clean(account.getUid())) : profile.getUid(),
                profile == null ? user.getEmail() : profile.getInstituteEmail(), branch.name(), branch.getLabel(),
                profile == null ? (account == null ? null : account.getSemester()) : Integer.valueOf(profile.getSemester()), readOnlyFields);
    }
    @Transactional public StudentProfileResponse createOwn(UserPrincipal principal, StudentProfileRequest request) {
        if (profiles.existsByUserId(principal.id())) throw new ProfileConflictException("PROFILE_ALREADY_EXISTS", "A student profile already exists for this account");
        String aadhaar = normalizedAadhaar(request.aadhaarNumber());
        if (aadhaar.isBlank()) throw new ProfileConflictException("AADHAAR_REQUIRED", "Aadhaar number is required when creating a profile");
        User user = users.findById(principal.id()).filter(u -> u.getRole() == Role.STUDENT).orElseThrow(ProfileNotFoundException::new);
        String uid = authoritativeUid(user, request.uid()); String instituteEmail = user.getEmail();
        Branch branch = Branch.COMPUTER_ENGINEERING;
        validateUnique(uid, instituteEmail, branch, request, null);
        StudentProfile profile = new StudentProfile(user);
        StudentAccount account = user.getStudentAccount();
        String studentName = account != null && hasValue(account.getStudentName()) ? clean(account.getStudentName()) : clean(request.studentName());
        int semester = account != null && account.getSemester() != null ? account.getSemester() : request.semester();
        apply(profile, request, uid, instituteEmail, branch, studentName, semester); protectAadhaar(profile, aadhaar);
        return save(profile);
    }
    @Transactional public StudentProfileResponse updateOwn(UserPrincipal principal, StudentProfileRequest request) {
        StudentProfile profile = profiles.findByUserId(principal.id()).orElseThrow(ProfileNotFoundException::new);
        StudentAccount account = profile.getUser().getStudentAccount();
        String studentName = account != null && hasValue(account.getStudentName()) ? profile.getStudentName() : clean(request.studentName());
        int semester = account != null && account.getSemester() != null ? profile.getSemester() : request.semester();
        validateUnique(profile.getUid(), profile.getInstituteEmail(), profile.getBranch(), request, profile.getId());
        apply(profile, request, profile.getUid(), profile.getInstituteEmail(), profile.getBranch(), studentName, semester);
        String aadhaar = normalizedAadhaar(request.aadhaarNumber()); if (!aadhaar.isBlank()) protectAadhaar(profile, aadhaar);
        return save(profile);
    }
    private StudentProfileResponse save(StudentProfile profile) {
        try { return response(profiles.saveAndFlush(profile)); }
        catch (DataIntegrityViolationException e) { throw new ProfileConflictException("PROFILE_CONFLICT", "UID, institute email, or roll number is already in use"); }
    }
    private void validateUnique(String uid, String email, Branch branch, StudentProfileRequest request, Long id) {
        String roll = clean(request.rollNumber());
        boolean duplicateUid = id == null ? profiles.existsByUidIgnoreCase(uid) : profiles.existsByUidIgnoreCaseAndIdNot(uid, id);
        if (duplicateUid) throw new ProfileConflictException("DUPLICATE_UID", "UID is already assigned to another student");
        boolean duplicateEmail = id == null ? profiles.existsByInstituteEmailIgnoreCase(email) : profiles.existsByInstituteEmailIgnoreCaseAndIdNot(email, id);
        if (duplicateEmail) throw new ProfileConflictException("DUPLICATE_INSTITUTE_EMAIL", "Institute email is already assigned to another student");
        boolean duplicateRoll = id == null ? profiles.existsByBranchAndAcademicYearAndRollNumberIgnoreCase(branch, request.academicYear(), roll)
                : profiles.existsByBranchAndAcademicYearAndRollNumberIgnoreCaseAndIdNot(branch, request.academicYear(), roll, id);
        if (duplicateRoll) throw new ProfileConflictException("DUPLICATE_ROLL_NUMBER", "Roll number is already in use for this branch and academic year");
    }
    private void apply(StudentProfile p, StudentProfileRequest r, String uid, String instituteEmail, Branch branch, String studentName, int semester) {
        p.apply(studentName, branch, uid, clean(r.rollNumber()), clean(r.permanentAddress()), nullable(r.currentAddress()),
                email(r.personalEmail()), instituteEmail, clean(r.phoneNumber()), clean(r.guardianName()), clean(r.guardianContact()),
                clean(r.emergencyContact()), r.bloodGroup(), clean(r.facultyMentorName()), clean(r.facultyMentorContact()),
                clean(r.internshipCoordinatorName()), clean(r.internshipCoordinatorContact()), clean(r.cdcCoordinatorName()),
                clean(r.cdcCoordinatorContact()), r.academicYear(), semester);
    }
    private void protectAadhaar(StudentProfile p, String aadhaar) { p.setEncryptedAadhaar(encryption.encrypt(aadhaar), aadhaar.substring(8)); }
    private static String authoritativeUid(User user, String submittedUid) {
        StudentAccount account = user.getStudentAccount();
        return account != null && hasValue(account.getUid()) ? clean(account.getUid()) : clean(submittedUid);
    }
    private static boolean hasValue(String value) { return value != null && !value.isBlank(); }
    private static String normalizedAadhaar(String value) { return value == null ? "" : value.replaceAll("[ -]", ""); }
    private static String clean(String value) { return value == null ? null : value.trim(); }
    private static String nullable(String value) { String clean = clean(value); return clean == null || clean.isBlank() ? null : clean; }
    private static String email(String value) { String clean = clean(value); return clean == null ? null : clean.toLowerCase(); }
    public static StudentProfileResponse response(StudentProfile p) {
        return new StudentProfileResponse(p.getId(), p.getStudentName(), p.getBranch().name(), p.getBranch().getLabel(), p.getUid(), p.getRollNumber(),
                p.getPermanentAddress(), p.getCurrentAddress(), p.getPersonalEmail(), p.getInstituteEmail(), p.getPhoneNumber(), p.getGuardianName(),
                p.getGuardianContact(), p.getEmergencyContact(), p.getBloodGroup(), "XXXX XXXX " + p.getAadhaarLastFour(), p.getFacultyMentorName(),
                p.getFacultyMentorContact(), p.getInternshipCoordinatorName(), p.getInternshipCoordinatorContact(), p.getCdcCoordinatorName(),
                p.getCdcCoordinatorContact(), p.getAcademicYear(), p.getSemester(), ProfileStatus.COMPLETE, p.getCreatedAt(), p.getUpdatedAt());
    }
}
