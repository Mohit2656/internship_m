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
    private final StudentProfileRepository profiles;
    private final UserRepository users;
    private final StudentAccountRepository accounts;
    private final AadhaarEncryptionService encryption;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public StudentProfileService(StudentProfileRepository profiles, UserRepository users, StudentAccountRepository accounts, AadhaarEncryptionService encryption,
            org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.profiles = profiles;
        this.users = users;
        this.accounts = accounts;
        this.encryption = encryption;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse getOwn(UserPrincipal principal) {
        return responseWithMentor(profiles.findByUserId(principal.id()).orElseThrow(ProfileNotFoundException::new));
    }

    @Transactional(readOnly = true)
    public ProfilePrefillResponse getOwnPrefill(UserPrincipal principal) {
        User user = users.findById(principal.id()).filter(u -> u.getRole() == Role.STUDENT).orElseThrow(ProfileNotFoundException::new);
        StudentAccount account = user.getStudentAccount();
        StudentProfile profile = profiles.findByUserId(principal.id()).orElse(null);
        var readOnlyFields = new ArrayList<String>();
        readOnlyFields.add("branch");
        readOnlyFields.add("facultyMentorName");
        readOnlyFields.add("facultyMentorContact");
        if (account != null && account.getSemester() != null) {
            readOnlyFields.add("semester");
        }
        Branch branch = profile == null ? Branch.COMPUTER_ENGINEERING : (profile.getBranch() != null ? profile.getBranch() : Branch.COMPUTER_ENGINEERING);
        MentorDetails mentor = mentorDetails(principal.id());
        Integer semester = profile != null ? profile.getSemester() : (account != null ? account.getSemester() : null);
        String name = profile != null ? profile.getStudentName() : (account != null ? clean(account.getStudentName()) : null);
        String uid = profile != null ? profile.getUid() : (account != null ? clean(account.getUid()) : null);
        String email = profile != null ? profile.getInstituteEmail() : user.getEmail();
        return new ProfilePrefillResponse(name, uid, email, branch.name(), branch.getLabel(), semester, mentor.name(), mentor.contact(), readOnlyFields);
    }

    @Transactional
    public StudentProfileResponse createOwn(UserPrincipal principal, StudentProfileRequest request) {
        if (profiles.existsByUserId(principal.id())) {
            throw new ProfileConflictException("PROFILE_ALREADY_EXISTS", "A student profile already exists for this account");
        }
        User user = users.findById(principal.id()).filter(u -> u.getRole() == Role.STUDENT).orElseThrow(ProfileNotFoundException::new);
        StudentAccount account = user.getStudentAccount();

        String uid = clean(request.uid());
        if (uid == null && account != null) {
            uid = clean(account.getUid());
        }

        String instituteEmail = email(request.instituteEmail());
        if (instituteEmail == null) {
            instituteEmail = email(user.getEmail());
        }

        Branch branch = Branch.COMPUTER_ENGINEERING;
        String studentName = clean(request.studentName());
        if (studentName == null && account != null) {
            studentName = clean(account.getStudentName());
        }

        Integer semester = account != null && account.getSemester() != null
                ? account.getSemester()
                : (request.semester() != null ? request.semester() : null);

        validateUnique(uid, instituteEmail, branch, request, null, principal.id());

        StudentProfile profile = new StudentProfile(user);
        MentorDetails mentor = mentorDetails(user.getId());
        String facultyMentorName = mentor.name() != null ? mentor.name() : clean(request.facultyMentorName());
        String facultyMentorContact = mentor.contact() != null ? mentor.contact() : clean(request.facultyMentorContact());

        profile.apply(studentName, branch, uid, uid, clean(request.permanentAddress()), nullable(request.currentAddress()),
                email(request.personalEmail()), instituteEmail, clean(request.phoneNumber()), clean(request.guardianName()), clean(request.guardianContact()),
                clean(request.emergencyContact()), clean(request.bloodGroup()), facultyMentorName, facultyMentorContact,
                clean(request.internshipCoordinatorName()), clean(request.internshipCoordinatorContact()), clean(request.cdcCoordinatorName()),
                clean(request.cdcCoordinatorContact()), clean(request.academicYear()), semester);
        profile.setCurrentCgpa(request.currentCgpa());

        String aadhaar = normalizedAadhaar(request.aadhaarNumber());
        if (!aadhaar.isBlank()) {
            protectAadhaar(profile, aadhaar);
        }
        return save(profile);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private edu.institution.ims.notification.StudentChangeNotificationService changeNotifications;

    @Transactional
    public StudentProfileResponse updateOwn(UserPrincipal principal, StudentProfileRequest request) {
        if (!WorkflowUserLocks.student(jdbc, principal.id(), true)) {
            throw new ProfileConflictException("STUDENT_ACCOUNT_INACTIVE", "Deactivated students cannot change their profile");
        }
        StudentProfile profile = profiles.findByUserId(principal.id()).orElseThrow(ProfileNotFoundException::new);
        StudentProfile before = new StudentProfile();
        before.apply(profile.getStudentName(), profile.getBranch(), profile.getUid(), profile.getRollNumber(),
                profile.getPermanentAddress(), profile.getCurrentAddress(), profile.getPersonalEmail(), profile.getInstituteEmail(),
                profile.getPhoneNumber(), profile.getGuardianName(), profile.getGuardianContact(), profile.getEmergencyContact(),
                profile.getBloodGroup(), profile.getFacultyMentorName(), profile.getFacultyMentorContact(),
                profile.getInternshipCoordinatorName(), profile.getInternshipCoordinatorContact(),
                profile.getCdcCoordinatorName(), profile.getCdcCoordinatorContact(),
                profile.getAcademicYear(), profile.getSemester());
        before.setCurrentCgpa(profile.getCurrentCgpa());
        StudentAccount account = profile.getUser().getStudentAccount();

        String studentName = request.studentName() != null && !request.studentName().isBlank()
                ? clean(request.studentName())
                : profile.getStudentName();

        Branch branch = profile.getBranch() != null
                ? profile.getBranch()
                : (request.branch() != null ? request.branch() : Branch.COMPUTER_ENGINEERING);

        String uid = request.uid() != null && !request.uid().isBlank()
                ? clean(request.uid())
                : profile.getUid();

        String instituteEmail = request.instituteEmail() != null && !request.instituteEmail().isBlank()
                ? email(request.instituteEmail())
                : profile.getInstituteEmail();

        Integer semester = account != null && account.getSemester() != null
                ? profile.getSemester()
                : (request.semester() != null ? request.semester() : profile.getSemester());

        validateUnique(uid, instituteEmail, branch, request, profile.getId(), principal.id());

        String permanentAddress = request.permanentAddress() != null && !request.permanentAddress().isBlank()
                ? clean(request.permanentAddress())
                : profile.getPermanentAddress();

        String currentAddress = request.currentAddress() != null
                ? nullable(request.currentAddress())
                : profile.getCurrentAddress();

        String personalEmail = request.personalEmail() != null && !request.personalEmail().isBlank()
                ? email(request.personalEmail())
                : profile.getPersonalEmail();

        String phoneNumber = request.phoneNumber() != null && !request.phoneNumber().isBlank()
                ? clean(request.phoneNumber())
                : profile.getPhoneNumber();

        String guardianName = request.guardianName() != null && !request.guardianName().isBlank()
                ? clean(request.guardianName())
                : profile.getGuardianName();

        String guardianContact = request.guardianContact() != null && !request.guardianContact().isBlank()
                ? clean(request.guardianContact())
                : profile.getGuardianContact();

        String emergencyContact = request.emergencyContact() != null && !request.emergencyContact().isBlank()
                ? clean(request.emergencyContact())
                : profile.getEmergencyContact();

        String bloodGroup = request.bloodGroup() != null && !request.bloodGroup().isBlank()
                ? clean(request.bloodGroup())
                : profile.getBloodGroup();

        String internshipCoordinatorName = request.internshipCoordinatorName() != null && !request.internshipCoordinatorName().isBlank()
                ? clean(request.internshipCoordinatorName())
                : profile.getInternshipCoordinatorName();

        String internshipCoordinatorContact = request.internshipCoordinatorContact() != null && !request.internshipCoordinatorContact().isBlank()
                ? clean(request.internshipCoordinatorContact())
                : profile.getInternshipCoordinatorContact();

        String cdcCoordinatorName = request.cdcCoordinatorName() != null && !request.cdcCoordinatorName().isBlank()
                ? clean(request.cdcCoordinatorName())
                : profile.getCdcCoordinatorName();

        String cdcCoordinatorContact = request.cdcCoordinatorContact() != null && !request.cdcCoordinatorContact().isBlank()
                ? clean(request.cdcCoordinatorContact())
                : profile.getCdcCoordinatorContact();

        String academicYear = request.academicYear() != null && !request.academicYear().isBlank()
                ? clean(request.academicYear())
                : profile.getAcademicYear();

        java.math.BigDecimal currentCgpa = request.currentCgpa() != null
                ? request.currentCgpa()
                : profile.getCurrentCgpa();

        MentorDetails mentor = mentorDetails(profile.getUser().getId());
        String facultyMentorName = mentor.name() != null
                ? mentor.name()
                : (request.facultyMentorName() != null && !request.facultyMentorName().isBlank()
                        ? clean(request.facultyMentorName())
                        : profile.getFacultyMentorName());

        String facultyMentorContact = mentor.contact() != null
                ? mentor.contact()
                : (request.facultyMentorContact() != null && !request.facultyMentorContact().isBlank()
                        ? clean(request.facultyMentorContact())
                        : profile.getFacultyMentorContact());

        profile.apply(studentName, branch, uid, uid, permanentAddress, currentAddress, personalEmail, instituteEmail,
                phoneNumber, guardianName, guardianContact, emergencyContact, bloodGroup, facultyMentorName, facultyMentorContact,
                internshipCoordinatorName, internshipCoordinatorContact, cdcCoordinatorName, cdcCoordinatorContact,
                academicYear, semester);
        profile.setCurrentCgpa(currentCgpa);

        String aadhaar = normalizedAadhaar(request.aadhaarNumber());
        if (!aadhaar.isBlank()) {
            protectAadhaar(profile, aadhaar);
        }

        StudentProfileResponse res = save(profile);
        if (changeNotifications != null) {
            changeNotifications.notifyProfileChanged(principal.id(), before, profile);
        }
        return res;
    }

    private StudentProfileResponse save(StudentProfile profile) {
        try {
            StudentProfile saved = profiles.saveAndFlush(profile);
            accounts.findByUserId(saved.getUser().getId()).ifPresent(account -> {
                if (saved.getUid() != null && !saved.getUid().isBlank()) {
                    account.setUid(saved.getUid());
                    accounts.save(account);
                }
            });
            return responseWithMentor(saved);
        } catch (DataIntegrityViolationException e) {
            throw new ProfileConflictException("PROFILE_CONFLICT", "UID, institute email, or roll number is already in use");
        }
    }

    private void validateUnique(String uid, String email, Branch branch, StudentProfileRequest request, Long id, Long userId) {
        if (uid != null && !uid.isBlank()) {
            boolean duplicateUid = id == null ? profiles.existsByUidIgnoreCase(uid) : profiles.existsByUidIgnoreCaseAndIdNot(uid, id);
            if (duplicateUid) throw new ProfileConflictException("DUPLICATE_UID", "UID is already assigned to another student");
            boolean duplicateAccountUid = accounts.existsByUidIgnoreCaseAndUserIdNot(uid, userId);
            if (duplicateAccountUid) throw new ProfileConflictException("DUPLICATE_UID", "UID is already assigned to another student");
        }
        if (email != null && !email.isBlank()) {
            boolean duplicateEmail = id == null ? profiles.existsByInstituteEmailIgnoreCase(email) : profiles.existsByInstituteEmailIgnoreCaseAndIdNot(email, id);
            if (duplicateEmail) throw new ProfileConflictException("DUPLICATE_INSTITUTE_EMAIL", "Institute email is already assigned to another student");
            if (users.existsByEmailIgnoreCaseAndIdNot(email, userId))
                throw new ProfileConflictException("DUPLICATE_INSTITUTE_EMAIL", "Institute email is already assigned to another account");
        }
    }

    private void protectAadhaar(StudentProfile p, String aadhaar) {
        p.setEncryptedAadhaar(encryption.encrypt(aadhaar), aadhaar.substring(8));
    }

    private static String normalizedAadhaar(String value) {
        return value == null ? "" : value.replaceAll("[ -]", "");
    }

    private static String clean(String value) {
        return value == null ? null : value.trim();
    }

    private static String nullable(String value) {
        String clean = clean(value);
        return clean == null || clean.isBlank() ? null : clean;
    }

    private static String email(String value) {
        String clean = clean(value);
        return clean == null ? null : clean.toLowerCase();
    }

    public static boolean isComplete(StudentProfile p) {
        if (p == null) return false;
        return hasText(p.getStudentName())
                && p.getBranch() != null
                && hasText(p.getUid())
                && hasText(p.getPermanentAddress())
                && hasText(p.getPersonalEmail())
                && hasText(p.getInstituteEmail())
                && hasText(p.getPhoneNumber())
                && hasText(p.getGuardianName())
                && hasText(p.getGuardianContact())
                && hasText(p.getEmergencyContact())
                && hasText(p.getBloodGroup())
                && hasText(p.getAadhaarLastFour())
                && hasText(p.getInternshipCoordinatorName())
                && hasText(p.getInternshipCoordinatorContact())
                && hasText(p.getCdcCoordinatorName())
                && hasText(p.getCdcCoordinatorContact())
                && hasText(p.getAcademicYear())
                && p.getSemester() != null;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private record MentorDetails(String name, String contact) {}

    private MentorDetails mentorDetails(Long studentId) {
        var rows = jdbc.query("select mp.name,mp.phone from mentor_student_assignments a "
                        + "join mentor_profiles mp on mp.user_id=a.mentor_user_id where a.student_user_id=? and a.active=true",
                (rs, row) -> new MentorDetails(rs.getString("name"), rs.getString("phone")), studentId);
        return rows.isEmpty() ? new MentorDetails(null, null) : rows.get(0);
    }

    StudentProfileResponse responseWithMentor(StudentProfile p) {
        StudentProfileResponse stored = response(p);
        MentorDetails mentor = mentorDetails(p.getUser().getId());
        String mentorName = mentor.name() != null ? mentor.name() : stored.facultyMentorName();
        String mentorContact = mentor.contact() != null ? mentor.contact() : stored.facultyMentorContact();
        return new StudentProfileResponse(stored.id(), stored.studentName(), stored.branch(), stored.branchLabel(), stored.uid(), stored.rollNumber(),
                stored.permanentAddress(), stored.currentAddress(), stored.personalEmail(), stored.instituteEmail(), stored.phoneNumber(),
                stored.guardianName(), stored.guardianContact(), stored.emergencyContact(), stored.bloodGroup(), stored.maskedAadhaar(),
                mentorName, mentorContact, stored.internshipCoordinatorName(), stored.internshipCoordinatorContact(),
                stored.cdcCoordinatorName(), stored.cdcCoordinatorContact(), stored.academicYear(), stored.semester(), stored.currentCgpa(),
                stored.profileStatus(), stored.createdAt(), stored.updatedAt());
    }

    public static StudentProfileResponse response(StudentProfile p) {
        ProfileStatus status = isComplete(p) ? ProfileStatus.COMPLETE : ProfileStatus.INCOMPLETE;
        return new StudentProfileResponse(p.getId(), p.getStudentName(),
                p.getBranch() == null ? null : p.getBranch().name(),
                p.getBranch() == null ? null : p.getBranch().getLabel(),
                p.getUid(), p.getRollNumber(),
                p.getPermanentAddress(), p.getCurrentAddress(), p.getPersonalEmail(), p.getInstituteEmail(), p.getPhoneNumber(), p.getGuardianName(),
                p.getGuardianContact(), p.getEmergencyContact(), p.getBloodGroup(),
                p.getAadhaarLastFour() == null ? null : "XXXX XXXX " + p.getAadhaarLastFour(),
                p.getFacultyMentorName(), p.getFacultyMentorContact(),
                p.getInternshipCoordinatorName(), p.getInternshipCoordinatorContact(),
                p.getCdcCoordinatorName(), p.getCdcCoordinatorContact(),
                p.getAcademicYear(), p.getSemester(), p.getCurrentCgpa(),
                status, p.getCreatedAt(), p.getUpdatedAt());
    }
}
