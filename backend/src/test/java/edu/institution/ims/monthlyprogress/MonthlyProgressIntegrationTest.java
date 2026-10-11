package edu.institution.ims.monthlyprogress;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.auth.LoginRequest;
import edu.institution.ims.internship.*;
import edu.institution.ims.mentor.*;
import edu.institution.ims.studentprofile.StudentProfileRepository;
import edu.institution.ims.user.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(classes = {edu.institution.ims.ImsApplication.class, MonthlyProgressIntegrationTest.FixedClockConfiguration.class})
class MonthlyProgressIntegrationTest {
    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    static final Instant NOW = Instant.parse("2026-11-10T06:30:00Z");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired StudentAccountRepository studentAccounts;
    @Autowired StudentProfileRepository studentProfiles;
    @Autowired MentorProfileRepository mentorProfiles;
    @Autowired MentorStudentAssignmentRepository assignments;
    @Autowired InternshipOnboardingRepository onboardings;
    @Autowired InternshipDetailsRepository internships;
    @Autowired MonthlyProgressReportRepository reports;
    @Autowired PasswordEncoder encoder;
    @Autowired MonthlyProgressReviewEventRepository reviewEvents;
    @Autowired org.springframework.jdbc.core.JdbcTemplate reviewJdbc;
    @Autowired edu.institution.ims.internship.storage.FileStorageService reviewStorage;

    User admin;
    User mentorA;
    User mentorB;
    User studentA;
    User studentB;

    @BeforeEach void setup() {
        clearData();
        admin = user("admin.progress@example.edu", "AdminPassword!23", Role.ADMIN);
        mentorA = mentor("mentor.progress.a@example.edu", "MentorPassword!23", "Mentor A", "MP100");
        mentorB = mentor("mentor.progress.b@example.edu", "MentorPassword!24", "Mentor B", "MP200");
        studentA = student("student.progress.a@example.edu", "StudentPassword!23", "Student A", "UID-P100");
        studentB = student("student.progress.b@example.edu", "StudentPassword!24", "Student B", "UID-P200");
        internship(studentA, "Alpha Ltd");
        internship(studentB, "Beta Ltd");
        assignments.save(new MentorStudentAssignment(mentorA, studentA, admin, NOW));
        assignments.save(new MentorStudentAssignment(mentorB, studentB, admin, NOW));
    }
    @AfterEach void cleanup() { clearData(); }

    @Test void uploadIsPendingDuplicatePeriodIsBlockedAndPrivateFilesRemainAccessible() throws Exception {
        Cookie student=loginCookie(studentA.getEmail(),"StudentPassword!23");
        mvc.perform(get("/api/v1/student/monthly-progress").cookie(student)).andExpect(status().isOk())
            .andExpect(jsonPath("$.periods[0].periodStart").value("2026-09-17")).andExpect(jsonPath("$.periods[2].status").value("UPCOMING"));
        MockMultipartFile source=png("progress.png");
        long id=create(student,source);
        mvc.perform(get("/api/v1/student/monthly-progress").cookie(student)).andExpect(status().isOk())
            .andExpect(jsonPath("$.periods[0].status").value("PENDING_MENTOR_REVIEW"));
        submit(student,1,png("duplicate.png"),"Duplicate").andExpect(status().isConflict());
        submit(student,3,png("future.png"),"Future").andExpect(status().isConflict());
        var downloaded=mvc.perform(get("/api/v1/student/monthly-progress/{id}/photo",id).cookie(student)).andExpect(status().isOk())
            .andExpect(header().string("Content-Type","image/png")).andReturn();
        assertThat(downloaded.getResponse().getContentAsByteArray()).isEqualTo(source.getBytes());
        assertThat(pdfText(student,id)).contains("PENDING_MENTOR_REVIEW","not yet approved").doesNotContain("GPS","EXIF","Geo-tag","Latitude","Longitude","workplace");
        mvc.perform(put("/api/v1/student/monthly-progress/{id}",id).cookie(student).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isMethodNotAllowed());
        assertThat(reviewJdbc.queryForObject("select count(*) from monthly_progress_review_events where report_id=? and event_type='SUBMITTED'",Integer.class,id)).isEqualTo(1);
    }
    @Test void onlyAssignedMentorCanReviewAndEveryReportAccessRemainsScoped() throws Exception {
        Cookie student=loginCookie(studentA.getEmail(),"StudentPassword!23"); long id=create(student,png("photo.png"));
        for(Cookie denied:List.of(student,loginCookie(admin.getEmail(),"AdminPassword!23"))) {
            decision(denied,id,true,1,null).andExpect(status().isForbidden());
            decision(denied,id,false,1,"Incorrect report").andExpect(status().isForbidden());
        }
        Cookie other=loginCookie(mentorB.getEmail(),"MentorPassword!24");
        decision(other,id,true,1,null).andExpect(status().isNotFound());
        decision(other,id,false,1,"Incorrect report").andExpect(status().isNotFound());
        for(String suffix:List.of("","/photo","/pdf")) mvc.perform(get("/api/v1/mentor/monthly-progress/"+id+suffix).cookie(other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/student/monthly-progress/{id}",id).cookie(loginCookie(studentB.getEmail(),"StudentPassword!24"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/mentor/monthly-progress/{id}/approve",id).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}")).andExpect(status().isUnauthorized());
        Cookie mentor=loginCookie(mentorA.getEmail(),"MentorPassword!23");
        decision(mentor,99999999,true,1,null).andExpect(status().isNotFound());
        decision(mentor,id,true,1,null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.reviewerId").value(mentorA.getId())).andExpect(jsonPath("$.reviewedAt").value(NOW.toString()));
        decision(mentor,id,false,1,"Too late").andExpect(status().isConflict());
        mvc.perform(get("/api/v1/student/monthly-progress").cookie(student)).andExpect(status().isOk()).andExpect(jsonPath("$.periods[0].status").value("APPROVED"));
        assertThat(pdfText(student,id)).contains("APPROVED",mentorA.getEmail()).doesNotContain("not yet approved","GPS");
        mvc.perform(get("/api/v1/admin/monthly-progress").cookie(loginCookie(admin.getEmail(),"AdminPassword!23"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("APPROVED"));
    }
    @Test void rejectionRequiresReasonAndResubmissionRetainsAllRevisionsAndEvidence() throws Exception {
        Cookie student=loginCookie(studentA.getEmail(),"StudentPassword!23"),mentor=loginCookie(mentorA.getEmail(),"MentorPassword!23");
        long id=create(student,png("original.png")); String originalKey=reports.findById(id).orElseThrow().getPhotoStorageKey();
        byte[] original=reviewStorage.load(originalKey).resource().getInputStream().readAllBytes();
        for(String reason:List.of(""," ","x".repeat(1001))) decision(mentor,id,false,1,reason).andExpect(status().isBadRequest());
        decision(mentor,id,false,1,null).andExpect(status().isBadRequest());
        decision(mentor,id,false,1,"  Explain your contribution  ").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.rejectionReason").value("Explain your contribution"));
        assertThat(pdfText(student,id)).contains("REJECTED","Explain your contribution").doesNotContain("GPS","EXIF");
        mvc.perform(get("/api/v1/student/monthly-progress").cookie(student)).andExpect(status().isOk()).andExpect(jsonPath("$.periods[0].report.rejectionReason").value("Explain your contribution"));
        resubmit(loginCookie(studentB.getEmail(),"StudentPassword!24"),id,1).andExpect(status().isNotFound());
        resubmit(student,id,2).andExpect(status().isConflict());
        resubmit(student,id,1).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_MENTOR_REVIEW"))
            .andExpect(jsonPath("$.revision").value(2)).andExpect(jsonPath("$.reviewerId").isEmpty()).andExpect(jsonPath("$.rejectionReason").isEmpty());
        decision(mentor,id,true,1,null).andExpect(status().isConflict()); decision(mentor,id,true,2,null).andExpect(status().isOk());
        assertThat(reviewJdbc.queryForList("select event_type from monthly_progress_review_events where report_id=? order by id",String.class,id))
            .containsExactly("SUBMITTED","REJECTED","SUBMITTED","APPROVED");
        assertThat(reviewJdbc.queryForObject("select photo_storage_key from monthly_progress_review_events where report_id=? and revision=1 and event_type='SUBMITTED'",String.class,id)).isEqualTo(originalKey);
        assertThat(reviewStorage.load(originalKey).resource().getInputStream().readAllBytes()).isEqualTo(original);
    }
    @Test void pendingAndApprovedCannotBeModifiedAndForgedApprovalIsIgnored() throws Exception {
        Cookie student=loginCookie(studentA.getEmail(),"StudentPassword!23");
        var created=mvc.perform(multipart("/api/v1/student/monthly-progress/1").file(png("photo.png"))
            .file(new MockMultipartFile("workDescription","","text/plain","Work".getBytes(StandardCharsets.UTF_8)))
            .param("status","APPROVED").param("geoVerificationStatus","VERIFIED").cookie(student))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING_MENTOR_REVIEW")).andExpect(jsonPath("$.geoVerificationStatus").doesNotExist()).andReturn();
        long id=json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        String key=reports.findById(id).orElseThrow().getPhotoStorageKey();
        resubmit(student,id,1).andExpect(status().isConflict());
        decision(loginCookie(mentorA.getEmail(),"MentorPassword!23"),id,true,1,null).andExpect(status().isOk());
        resubmit(student,id,1).andExpect(status().isConflict());submit(student,1,png("replacement.png"),"Changed").andExpect(status().isConflict());
        assertThat(reports.findById(id).orElseThrow().getPhotoStorageKey()).isEqualTo(key);
        assertThat(reports.findById(id).orElseThrow().getWorkDescription()).isEqualTo("Work");
    }
    @Test void genuineJpgJpegAndPngWithoutGpsAreAcceptedButCorruptImagesFail() throws Exception {
        var validator=new ProgressPhotoValidator(20971520);
        for(String name:List.of("photo.jpg","photo.jpeg","photo.png")) {
            byte[] bytes=ProgressPhotoValidatorTest.plain(name.endsWith("png")?"png":"jpeg");
            var photo=new MockMultipartFile("photo",name,name.endsWith("png")?"image/png":"image/jpeg",bytes);
            var validated=validator.validate(photo);assertThat(validated.bytes()).isEqualTo(bytes);assertThat(validated.geoVerificationStatus()).isNull();
        }
        Cookie student=loginCookie(studentA.getEmail(),"StudentPassword!23");
        submit(student,1,new MockMultipartFile("photo","corrupt.jpg","image/jpeg",new byte[]{1,2,3}),"Work").andExpect(status().isBadRequest());
        long id=create(student,exifJpeg("gps.jpeg",91,0,0,79,0,0));
        mvc.perform(get("/api/v1/mentor/monthly-progress/{id}",id).cookie(loginCookie(mentorA.getEmail(),"MentorPassword!23")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_MENTOR_REVIEW")).andExpect(jsonPath("$.exifLatitude").doesNotExist());
        assertThat(reports.findById(id).orElseThrow().getWorkplaceVerificationStatus()).isNull();
    }
    @Test void auditFailureRollsBackDecisionAndResubmission() throws Exception {
        Cookie student=loginCookie(studentA.getEmail(),"StudentPassword!23"),mentor=loginCookie(mentorA.getEmail(),"MentorPassword!23");
        long id=create(student,png("original.png"));
        String original=reports.findById(id).orElseThrow().getPhotoStorageKey();
        reviewJdbc.update("insert into monthly_progress_review_events(report_id,revision,event_type,actor_id,performed_at) values (?,1,'APPROVED',?,?)",id,mentorA.getId(),java.sql.Timestamp.from(NOW));
        decision(mentor,id,true,1,null).andExpect(status().isConflict());
        assertThat(reports.findById(id).orElseThrow().getStatus()).isEqualTo(MonthlyProgressStatus.PENDING_MENTOR_REVIEW);
        reviewJdbc.update("delete from monthly_progress_review_events where report_id=? and event_type='APPROVED'",id);
        decision(mentor,id,false,1,"Correct this").andExpect(status().isOk());
        reviewJdbc.update("insert into monthly_progress_review_events(report_id,revision,event_type,actor_id,performed_at) values (?,2,'SUBMITTED',?,?)",id,studentA.getId(),java.sql.Timestamp.from(NOW));
        java.nio.file.Path storageRoot=reviewStorage.load(original).resource().getFile().toPath().getParent();
        java.util.Set<String> filesBefore;
        try(var files=java.nio.file.Files.list(storageRoot)) { filesBefore=files.map(path->path.getFileName().toString()).collect(java.util.stream.Collectors.toSet()); }
        resubmit(student,id,1).andExpect(status().isConflict());
        var retained=reports.findById(id).orElseThrow(); assertThat(retained.getRevision()).isEqualTo(1);assertThat(retained.getStatus()).isEqualTo(MonthlyProgressStatus.REJECTED);
        assertThat(retained.getPhotoStorageKey()).isEqualTo(original);assertThat(reviewStorage.load(original)).isNotNull();
        try(var files=java.nio.file.Files.list(storageRoot)) { assertThat(files.map(path->path.getFileName().toString()).collect(java.util.stream.Collectors.toSet())).isEqualTo(filesBefore); }
    }
    @Test void monthlyExportsUseReviewStatusAndMentorScope() throws Exception {
        Cookie student=loginCookie(studentA.getEmail(),"StudentPassword!23"),mentor=loginCookie(mentorA.getEmail(),"MentorPassword!23");long id=create(student,png("export.png"));
        decision(mentor,id,true,1,null).andExpect(status().isOk());
        List<String> fields=List.of("uid","reviewStatus","imageSha256");
        String body=json.writeValueAsString(Map.of("reportType","MONTHLY_PROGRESS","fields",fields,"filters",Map.of("monthlyStatus","APPROVED"),"studentIds",List.of(studentA.getId()),"page",0,"format","CSV"));
        var csv=mvc.perform(post("/api/v1/admin/reports/generate").cookie(loginCookie(admin.getEmail(),"AdminPassword!23")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn();
        assertThat(csv.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("APPROVED","Image SHA-256").doesNotContain("GPS","EXIF","Geo Tag");
        mvc.perform(post("/api/v1/mentor/reports/table").cookie(mentor).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].reviewStatus").value("APPROVED"));
        mvc.perform(post("/api/v1/mentor/reports/generate").cookie(loginCookie(mentorB.getEmail(),"MentorPassword!24")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
    }
    private long create(Cookie student,MockMultipartFile photo) throws Exception {
        var result=submit(student,1,photo,"Work").andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING_MENTOR_REVIEW"))
            .andExpect(jsonPath("$.revision").value(1)).andExpect(jsonPath("$.reviewerId").isEmpty()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
    private ResultActions decision(Cookie mentor,long id,boolean approve,int revision,String reason) throws Exception {
        var body=new HashMap<String,Object>();body.put("revision",revision);if(reason!=null)body.put("reason",reason);
        return mvc.perform(post("/api/v1/mentor/monthly-progress/{id}/{decision}",id,approve?"approve":"reject").cookie(mentor).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
    private ResultActions resubmit(Cookie student,long id,int revision) throws Exception {
        return mvc.perform(multipart("/api/v1/student/monthly-progress/{id}/resubmit",id).file(png("corrected.png"))
            .file(new MockMultipartFile("workDescription","","text/plain","Corrected work".getBytes(StandardCharsets.UTF_8))).param("revision",Integer.toString(revision)).cookie(student));
    }
    private String pdfText(Cookie student,long id) throws Exception {
        byte[] pdf=mvc.perform(get("/api/v1/student/monthly-progress/{id}/pdf",id).cookie(student)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try(var document=org.apache.pdfbox.pdmodel.PDDocument.load(pdf)) { return new org.apache.pdfbox.text.PDFTextStripper().getText(document); }
    }

    private ResultActions submit(Cookie session, int month, MockMultipartFile photo, String work) throws Exception {
        return mvc.perform(multipart("/api/v1/student/monthly-progress/{month}", month).file(photo)
                .file(new MockMultipartFile("workDescription", "", "text/plain", work.getBytes(StandardCharsets.UTF_8))).cookie(session));
    }
    private MockMultipartFile png(String name) throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream(); ImageIO.write(image, "png", output);
        return new MockMultipartFile("photo", name, "image/png", output.toByteArray());
    }
    private MockMultipartFile exifJpeg(String name, int latDeg, int latMin, int latSec, int lonDeg, int lonMin, int lonSec) throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream(); ImageIO.write(image, "jpeg", jpeg);
        byte[] source = jpeg.toByteArray(); byte[] exif = gpsExif(latDeg, latMin, latSec, lonDeg, lonMin, lonSec);
        ByteArrayOutputStream result = new ByteArrayOutputStream(); result.write(source, 0, 2); result.write(0xff); result.write(0xe1);
        int length = exif.length + 2; result.write((length >>> 8) & 0xff); result.write(length & 0xff); result.write(exif); result.write(source, 2, source.length - 2);
        return new MockMultipartFile("photo", name, "image/jpeg", result.toByteArray());
    }
    private byte[] gpsExif(int latDeg, int latMin, int latSec, int lonDeg, int lonMin, int lonSec) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); out.write("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
        out.write('I'); out.write('I'); le16(out, 42); le32(out, 8); le16(out, 1);
        le16(out, 0x8825); le16(out, 4); le32(out, 1); le32(out, 26); le32(out, 0);
        le16(out, 4);
        le16(out, 1); le16(out, 2); le32(out, 2); out.write('N'); out.write(0); out.write(0); out.write(0);
        le16(out, 2); le16(out, 5); le32(out, 3); le32(out, 80);
        le16(out, 3); le16(out, 2); le32(out, 2); out.write('E'); out.write(0); out.write(0); out.write(0);
        le16(out, 4); le16(out, 5); le32(out, 3); le32(out, 104); le32(out, 0);
        rational(out, latDeg); rational(out, latMin); rational(out, latSec);
        rational(out, lonDeg); rational(out, lonMin); rational(out, lonSec);
        return out.toByteArray();
    }
    private static void rational(ByteArrayOutputStream out, int value) { le32(out, value); le32(out, 1); }
    private static void le16(ByteArrayOutputStream out, int value) { out.write(value & 0xff); out.write((value >>> 8) & 0xff); }
    private static void le32(ByteArrayOutputStream out, int value) { le16(out, value); le16(out, value >>> 16); }
    private User user(String email, String password, Role role) { return users.save(new User(email, encoder.encode(password), role, true)); }
    private User mentor(String email, String password, String name, String employeeId) {
        User mentor = user(email, password, Role.MENTOR); mentorProfiles.save(new MentorProfile(mentor, name, employeeId, "Computer Engineering", "Professor", "+91 9876543210")); return mentor;
    }
    private User student(String email, String password, String name, String uid) {
        User student = user(email, password, Role.STUDENT); studentAccounts.save(new StudentAccount(student, name, uid, 6)); return student;
    }
    private void internship(User student, String company) {
        InternshipOnboarding onboarding = onboardings.saveAndFlush(new InternshipOnboarding(student)); onboarding.markDocumentsComplete(); onboarding.confirmDiary();
        InternshipDetails details = new InternshipDetails(onboarding); details.apply(company, CompanyType.IT, null, "Company address", "+91 9876543210", "Supervisor", "Manager", "+91 9876543211", 4, LocalDate.of(2026, 9, 17), LocalDate.of(2027, 1, 16), 17, InternshipMode.OFFICE_REPORTING, BigDecimal.ZERO, false);
        internships.saveAndFlush(details); onboarding.markInternshipDetailsComplete(); onboardings.saveAndFlush(onboarding);
    }
    private Cookie loginCookie(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("ims_auth");
    }
    private void clearData() {
        reviewEvents.deleteAll(); reports.deleteAll(); assignments.deleteAll(); internships.deleteAll(); onboardings.deleteAll(); studentProfiles.deleteAll(); mentorProfiles.deleteAll(); studentAccounts.deleteAll(); users.deleteAll();
    }
    @TestConfiguration static class FixedClockConfiguration { @Bean @Primary Clock fixedMonthlyProgressClock() { return Clock.fixed(NOW, ZONE); } }
}
