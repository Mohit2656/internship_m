package edu.institution.ims.studentprofile;

import edu.institution.ims.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "student_profiles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_student_profiles_user", columnNames = "user_id"),
        @UniqueConstraint(name = "uk_student_profiles_uid", columnNames = "uid"),
        @UniqueConstraint(name = "uk_student_profiles_institute_email", columnNames = "institute_email"),
        @UniqueConstraint(name = "uk_student_profiles_roll_identity", columnNames = {"branch", "academic_year", "roll_number"})
}, indexes = {
        @Index(name = "idx_student_profiles_name", columnList = "student_name"),
        @Index(name = "idx_student_profiles_branch", columnList = "branch"),
        @Index(name = "idx_student_profiles_academic_year", columnList = "academic_year"),
        @Index(name = "idx_student_profiles_semester", columnList = "semester")
})
public class StudentProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_student_profiles_user")) private User user;
    @Column(name = "student_name", nullable = false, length = 150) private String studentName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private Branch branch;
    @Column(nullable = false, length = 50) private String uid;
    @Column(name = "roll_number", nullable = false, length = 50) private String rollNumber;
    @Column(name = "permanent_address", nullable = false, length = 1000) private String permanentAddress;
    @Column(name = "current_address", length = 1000) private String currentAddress;
    @Column(name = "personal_email", nullable = false, length = 254) private String personalEmail;
    @Column(name = "institute_email", nullable = false, length = 254) private String instituteEmail;
    @Column(name = "phone_number", nullable = false, length = 25) private String phoneNumber;
    @Column(name = "guardian_name", nullable = false, length = 150) private String guardianName;
    @Column(name = "guardian_contact", nullable = false, length = 25) private String guardianContact;
    @Column(name = "emergency_contact", nullable = false, length = 25) private String emergencyContact;
    @Column(name = "blood_group", nullable = false, length = 5) private String bloodGroup;
    @Column(name = "aadhaar_encrypted", nullable = false, length = 255) private String aadhaarEncrypted;
    @Column(name = "aadhaar_last_four", nullable = false, length = 4) private String aadhaarLastFour;
    @Column(name = "faculty_mentor_name", nullable = false, length = 150) private String facultyMentorName;
    @Column(name = "faculty_mentor_contact", nullable = false, length = 25) private String facultyMentorContact;
    @Column(name = "internship_coordinator_name", nullable = false, length = 150) private String internshipCoordinatorName;
    @Column(name = "internship_coordinator_contact", nullable = false, length = 25) private String internshipCoordinatorContact;
    @Column(name = "cdc_coordinator_name", nullable = false, length = 150) private String cdcCoordinatorName;
    @Column(name = "cdc_coordinator_contact", nullable = false, length = 25) private String cdcCoordinatorContact;
    @Column(name = "academic_year", nullable = false, length = 9) private String academicYear;
    @Column(nullable = false) private int semester;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected StudentProfile() {}
    public StudentProfile(User user) { this.user = user; }
    @PrePersist void created() { var now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void updated() { updatedAt = Instant.now(); }
    public Long getId() { return id; } public User getUser() { return user; }
    public String getStudentName() { return studentName; } public Branch getBranch() { return branch; } public String getUid() { return uid; }
    public String getRollNumber() { return rollNumber; } public String getPermanentAddress() { return permanentAddress; } public String getCurrentAddress() { return currentAddress; }
    public String getPersonalEmail() { return personalEmail; } public String getInstituteEmail() { return instituteEmail; } public String getPhoneNumber() { return phoneNumber; }
    public String getGuardianName() { return guardianName; } public String getGuardianContact() { return guardianContact; } public String getEmergencyContact() { return emergencyContact; }
    public String getBloodGroup() { return bloodGroup; } public String getAadhaarLastFour() { return aadhaarLastFour; }
    public String getFacultyMentorName() { return facultyMentorName; } public String getFacultyMentorContact() { return facultyMentorContact; }
    public String getInternshipCoordinatorName() { return internshipCoordinatorName; } public String getInternshipCoordinatorContact() { return internshipCoordinatorContact; }
    public String getCdcCoordinatorName() { return cdcCoordinatorName; } public String getCdcCoordinatorContact() { return cdcCoordinatorContact; }
    public String getAcademicYear() { return academicYear; } public int getSemester() { return semester; }
    public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
    public String getAadhaarEncrypted() { return aadhaarEncrypted; }

    public void apply(String studentName, Branch branch, String uid, String rollNumber, String permanentAddress, String currentAddress,
            String personalEmail, String instituteEmail, String phoneNumber, String guardianName, String guardianContact, String emergencyContact,
            String bloodGroup, String facultyMentorName, String facultyMentorContact, String internshipCoordinatorName,
            String internshipCoordinatorContact, String cdcCoordinatorName, String cdcCoordinatorContact, String academicYear, int semester) {
        this.studentName = studentName; this.branch = branch; this.uid = uid; this.rollNumber = rollNumber; this.permanentAddress = permanentAddress;
        this.currentAddress = currentAddress; this.personalEmail = personalEmail; this.instituteEmail = instituteEmail; this.phoneNumber = phoneNumber;
        this.guardianName = guardianName; this.guardianContact = guardianContact; this.emergencyContact = emergencyContact; this.bloodGroup = bloodGroup;
        this.facultyMentorName = facultyMentorName; this.facultyMentorContact = facultyMentorContact; this.internshipCoordinatorName = internshipCoordinatorName;
        this.internshipCoordinatorContact = internshipCoordinatorContact; this.cdcCoordinatorName = cdcCoordinatorName; this.cdcCoordinatorContact = cdcCoordinatorContact;
        this.academicYear = academicYear; this.semester = semester;
    }
    public void setEncryptedAadhaar(String encrypted, String lastFour) { this.aadhaarEncrypted = encrypted; this.aadhaarLastFour = lastFour; }
}

