package edu.institution.ims.mentor;

import edu.institution.ims.professionalprofile.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.studentprofile.*;
import edu.institution.ims.user.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class MentorStudentProfileService {
    private final MentorStudentAssignmentRepository assignments;
    private final StudentProfileRepository profiles;
    private final StudentProfessionalProfileRepository professionalProfiles;
    private final StudentSkillRepository skills;
    private final UserRepository users;

    public MentorStudentProfileService(MentorStudentAssignmentRepository assignments,
            StudentProfileRepository profiles,
            StudentProfessionalProfileRepository professionalProfiles,
            StudentSkillRepository skills,
            UserRepository users) {
        this.assignments = assignments;
        this.profiles = profiles;
        this.professionalProfiles = professionalProfiles;
        this.skills = skills;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public MentorStudentProfileResponse getAssignedStudent(Long studentId, UserPrincipal principal) {
        requireMentor(principal.id());
        MentorStudentAssignment assignment = assignments.findByStudentIdAndActiveTrue(studentId)
                .filter(value -> value.getMentor().getId().equals(principal.id()))
                .orElseThrow(() -> error("ASSIGNED_STUDENT_NOT_FOUND", "Student was not found in your assigned students"));
        StudentProfile profile = profiles.findByUserId(assignment.getStudent().getId())
                .orElseThrow(() -> error("STUDENT_PROFILE_NOT_FOUND", "Student profile is not available"));
        StudentProfessionalProfile professional = professionalProfiles.findByStudentId(studentId).orElse(null);
        List<String> confirmedSkills = professional == null ? List.of() : skills
                .findAllByProfessionalProfileIdAndActiveTrueOrderByDisplayNameAsc(professional.getId())
                .stream().map(StudentSkill::getDisplayName).toList();
        return new MentorStudentProfileResponse(profile.getStudentName(), profile.getUid(),
                profile.getBranch() == null ? null : profile.getBranch().name(),
                profile.getBranch() == null ? null : profile.getBranch().getLabel(),
                profile.getRollNumber(), profile.getInstituteEmail(), profile.getPersonalEmail(),
                profile.getPhoneNumber(), profile.getAcademicYear(), profile.getSemester(),
                professional == null ? null : professional.getHeadline(),
                professional == null ? null : professional.getCareerInterests(), confirmedSkills,
                profile.getUpdatedAt() == null ? profile.getCreatedAt() : profile.getUpdatedAt(),
                professional == null ? null : professional.getUpdatedAt() == null ? professional.getCreatedAt() : professional.getUpdatedAt(),
                professional == null ? null : skills.lastStudentUpdateByProfileId(professional.getId()));
    }

    private void requireMentor(Long userId) {
        users.findById(userId).filter(user -> user.getRole() == Role.MENTOR)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));
    }

    private static ResponseStatusException error(String code, String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
