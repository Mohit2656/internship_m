package edu.institution.ims.mentor;

import edu.institution.ims.internship.*;
import edu.institution.ims.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.Cookie;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class MentorInternshipDetailsIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private MentorStudentAssignmentRepository assignments;
    @Autowired private InternshipOnboardingRepository onboardings;
    @Autowired private InternshipDetailsRepository detailsRepository;
    @Autowired private edu.institution.ims.attendance.InternshipLocationRepository locations;
    @Autowired private PasswordEncoder encoder;
    @Autowired private Clock clock;

    private User mentor;
    private User otherMentor;
    private User student;
    private Cookie mentorCookie;
    private Cookie otherMentorCookie;

    @BeforeEach
    void setUp() throws Exception {
        mentor = users.save(new User("mentor.det.test@example.edu", encoder.encode("MentorPassword!23"), Role.MENTOR, true));
        otherMentor = users.save(new User("other.mentor.test@example.edu", encoder.encode("MentorPassword!23"), Role.MENTOR, true));
        student = users.save(new User("student.det.test@example.edu", encoder.encode("StudentPassword!23"), Role.STUDENT, true));

        assignments.save(new MentorStudentAssignment(mentor, student, mentor, Instant.now(clock)));

        mentorCookie = login("mentor.det.test@example.edu", "MentorPassword!23");
        otherMentorCookie = login("other.mentor.test@example.edu", "MentorPassword!23");

        // Create onboarding and internship details for Internship 1 and Internship 2
        InternshipOnboarding ob1 = onboardings.save(new InternshipOnboarding(student, (short) 1));
        ob1.confirmDiary();
        ob1.markInternshipDetailsComplete();
        onboardings.save(ob1);

        InternshipDetails det1 = new InternshipDetails(ob1);
        det1.apply("Company One", CompanyType.IT, null, "Address 1", "9876543210", "Sup 1", "Lead", "sup1@example.com", 3, LocalDate.now(clock), LocalDate.now(clock).plusMonths(3), 12, InternshipMode.OFFICE_REPORTING, BigDecimal.valueOf(15000), true);
        detailsRepository.save(det1);

        InternshipOnboarding ob2 = onboardings.save(new InternshipOnboarding(student, (short) 2));
        ob2.confirmDiary();
        ob2.markInternshipDetailsComplete();
        onboardings.save(ob2);

        InternshipDetails det2 = new InternshipDetails(ob2);
        det2.apply("Company Two", CompanyType.CORE, null, "Address 2", "9876543211", "Sup 2", "Manager", "sup2@example.com", 6, LocalDate.now(clock), LocalDate.now(clock).plusMonths(6), 24, InternshipMode.ONLINE, BigDecimal.valueOf(25000), false);
        detailsRepository.save(det2);
    }

    private Cookie login(String email, String password) throws Exception {
        var result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("ims_auth");
    }

    @Test
    void assignedMentorCanFetchAllInternshipsForStudent() throws Exception {
        mvc.perform(get("/api/v1/mentor/students/{studentId}/internships", student.getId())
                .cookie(mentorCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].companyName").value("Company One"))
                .andExpect(jsonPath("$[0].internshipNumber").value(1))
                .andExpect(jsonPath("$[1].companyName").value("Company Two"))
                .andExpect(jsonPath("$[1].internshipNumber").value(2));
    }

    @Test
    void unassignedMentorIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/mentor/students/{studentId}/internships", student.getId())
                .cookie(otherMentorCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignedMentorCanApproveWorkplaceIndependently() throws Exception {
        // Register workplace for Internship 1 and 2
        InternshipDetails det1 = detailsRepository.findAllByOnboardingStudentIdOrderByOnboardingInternshipNumber(student.getId()).get(0);
        InternshipDetails det2 = detailsRepository.findAllByOnboardingStudentIdOrderByOnboardingInternshipNumber(student.getId()).get(1);

        // Register directly via repository for test verification
        edu.institution.ims.attendance.InternshipLocation loc1 = new edu.institution.ims.attendance.InternshipLocation(
                det1, BigDecimal.valueOf(21.123456), BigDecimal.valueOf(79.123456), BigDecimal.valueOf(200), Instant.now(clock));
        loc1.selectedPlace("place-1", "Nagpur Branch Address");
        loc1.name("Company One Branch");
        locations.save(loc1);
        det1.attachWorkplace(loc1);
        detailsRepository.save(det1);

        edu.institution.ims.attendance.InternshipLocation loc2 = new edu.institution.ims.attendance.InternshipLocation(
                det2, BigDecimal.valueOf(19.076123), BigDecimal.valueOf(72.877456), BigDecimal.valueOf(200), Instant.now(clock));
        loc2.selectedPlace("place-2", "Mumbai Branch Address");
        loc2.name("Company Two Branch");
        locations.save(loc2);
        det2.attachWorkplace(loc2);
        detailsRepository.save(det2);

        // Fetch location for Internship 2 as mentor
        mvc.perform(get("/api/v1/mentor/students/{studentId}/internship/location", student.getId())
                .param("internshipId", String.valueOf(det2.getId()))
                .cookie(mentorCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Company Two"))
                .andExpect(jsonPath("$.status").value("PENDING_MENTOR_APPROVAL"));

        // Approve Internship 2 workplace as mentor
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/mentor/students/{studentId}/internship/location/review", student.getId())
                .param("internshipId", String.valueOf(det2.getId()))
                .cookie(mentorCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"revision\":1,\"decision\":\"APPROVE\",\"remarks\":\"Approved Mumbai office\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.coordinatesLocked").value(true));

        // Verify Internship 1 location remains PENDING_MENTOR_APPROVAL
        mvc.perform(get("/api/v1/mentor/students/{studentId}/internship/location", student.getId())
                .param("internshipId", String.valueOf(det1.getId()))
                .cookie(mentorCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Company One"))
                .andExpect(jsonPath("$.status").value("PENDING_MENTOR_APPROVAL"));
    }

    @Test
    void assignedMentorCanRejectWorkplaceWithReason() throws Exception {
        InternshipDetails det1 = detailsRepository.findAllByOnboardingStudentIdOrderByOnboardingInternshipNumber(student.getId()).get(0);
        edu.institution.ims.attendance.InternshipLocation loc1 = new edu.institution.ims.attendance.InternshipLocation(
                det1, BigDecimal.valueOf(21.123456), BigDecimal.valueOf(79.123456), BigDecimal.valueOf(200), Instant.now(clock));
        loc1.selectedPlace("place-1", "Nagpur Branch Address");
        locations.save(loc1);
        det1.attachWorkplace(loc1);
        detailsRepository.save(det1);

        // Rejection without reason fails
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/mentor/students/{studentId}/internship/location/review", student.getId())
                .param("internshipId", String.valueOf(det1.getId()))
                .cookie(mentorCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"revision\":1,\"decision\":\"REJECT\",\"remarks\":\"\"}"))
                .andExpect(status().isBadRequest());

        // Rejection with valid reason succeeds
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/mentor/students/{studentId}/internship/location/review", student.getId())
                .param("internshipId", String.valueOf(det1.getId()))
                .cookie(mentorCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"revision\":1,\"decision\":\"REJECT\",\"remarks\":\"Incorrect branch selected, select actual office.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.mentorRemarks").value("Incorrect branch selected, select actual office."));
    }
}
