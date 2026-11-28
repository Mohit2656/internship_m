package edu.institution.ims.mentor;

import edu.institution.ims.attendance.InternshipLocation;
import edu.institution.ims.attendance.InternshipLocationRepository;
import edu.institution.ims.internship.InternshipDetails;
import edu.institution.ims.internship.InternshipDetailsRepository;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.studentprofile.StudentProfile;
import edu.institution.ims.studentprofile.StudentProfileRepository;
import edu.institution.ims.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/student/mentor")
public class StudentMentorController {
    public record StudentMentorResponse(
            String mentorName,
            String mentorEmail,
            String internship1MentorName,
            String internship2MentorName
    ) {}

    private final MentorStudentAssignmentRepository assignments;
    private final MentorProfileRepository mentorProfiles;
    private final StudentProfileRepository studentProfiles;
    private final InternshipDetailsRepository details;
    private final InternshipLocationRepository locations;

    public StudentMentorController(MentorStudentAssignmentRepository assignments,
                                   MentorProfileRepository mentorProfiles,
                                   StudentProfileRepository studentProfiles,
                                   InternshipDetailsRepository details,
                                   InternshipLocationRepository locations) {
        this.assignments = assignments;
        this.mentorProfiles = mentorProfiles;
        this.studentProfiles = studentProfiles;
        this.details = details;
        this.locations = locations;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public StudentMentorResponse get(@AuthenticationPrincipal UserPrincipal principal) {
        String generalMentor = null;
        String generalEmail = null;

        var activeAssignment = assignments.findByStudentIdAndActiveTrue(principal.id()).orElse(null);
        if (activeAssignment != null) {
            User assignedMentor = activeAssignment.getMentor();
            generalMentor = resolveMentorName(assignedMentor);
            generalEmail = assignedMentor.getEmail();
        } else {
            StudentProfile sp = studentProfiles.findByUserId(principal.id()).orElse(null);
            if (sp != null && sp.getFacultyMentorName() != null && !sp.getFacultyMentorName().isBlank()) {
                generalMentor = sp.getFacultyMentorName().trim();
            }
        }

        List<InternshipDetails> all = details.findAllByOnboardingStudentIdOrderByOnboardingInternshipNumber(principal.id());
        InternshipDetails int1 = all.stream().filter(i -> i.getOnboarding().getInternshipNumber() == 1).findFirst().orElse(null);
        InternshipDetails int2 = all.stream().filter(i -> i.getOnboarding().getInternshipNumber() == 2).findFirst().orElse(null);

        String int1Mentor = generalMentor;
        if (int1 != null) {
            InternshipLocation loc1 = locations.findByInternshipId(int1.getId()).orElse(null);
            if (loc1 != null && loc1.getMentorReviewer() != null) {
                int1Mentor = resolveMentorName(loc1.getMentorReviewer());
            }
        }

        // Automatic Mentor Assignment: Internship 2 automatically inherits the same mentor as Internship 1
        String int2Mentor = int1Mentor;
        if (int2 != null) {
            InternshipLocation loc2 = locations.findByInternshipId(int2.getId()).orElse(null);
            if (loc2 != null && loc2.getMentorReviewer() != null) {
                int2Mentor = resolveMentorName(loc2.getMentorReviewer());
            }
        }

        return new StudentMentorResponse(generalMentor, generalEmail, int1Mentor, int2Mentor);
    }

    private String resolveMentorName(User mentor) {
        return mentorProfiles.findByUserId(mentor.getId())
                .map(MentorProfile::getName)
                .orElse(mentor.getEmail());
    }
}
