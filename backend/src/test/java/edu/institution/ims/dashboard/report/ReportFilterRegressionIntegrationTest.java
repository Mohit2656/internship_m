package edu.institution.ims.dashboard.report;

import com.fasterxml.jackson.databind.*;
import edu.institution.ims.attendance.*;
import edu.institution.ims.internship.*;
import edu.institution.ims.mentor.*;
import edu.institution.ims.monthlyprogress.*;
import edu.institution.ims.security.*;
import edu.institution.ims.studentprofile.*;
import edu.institution.ims.studentprofile.security.AadhaarEncryptionService;
import edu.institution.ims.user.*;
import edu.institution.ims.user.Role;
import edu.institution.ims.weeklyprogress.WeeklyProgressService;
import jakarta.servlet.http.Cookie;
import java.io.*;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:report_filter_matrix;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional @TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ContextConfiguration(classes={edu.institution.ims.ImsApplication.class,ReportFilterRegressionIntegrationTest.FixedClock.class})
class ReportFilterRegressionIntegrationTest {
    static final LocalDate TODAY=LocalDate.of(2026,10,30);
    static final Instant NOW=TODAY.atTime(12,0).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
    static final String PASSWORD="LocalReportTest!2026";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired StudentAccountRepository accounts;
    @Autowired StudentProfileRepository profiles;
    @Autowired MentorProfileRepository mentors;
    @Autowired MentorStudentAssignmentRepository assignments;
    @Autowired InternshipOnboardingRepository onboardings;
    @Autowired InternshipDetailsRepository internships;
    @Autowired InternshipDocumentRepository documents;
    @Autowired AttendanceRepository attendance;
    @Autowired MonthlyProgressReportRepository monthly;
    @Autowired WeeklyProgressService weekly;
    @Autowired InternshipCompletionService completions;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtService jwt;
    @Autowired AadhaarEncryptionService encryption;
    @Autowired PlatformTransactionManager transactions;
    User admin,mentorA,mentorB;
    Long sessionA,sessionB;
    String passwordHash;
    final List<Dummy> students=new ArrayList<>();
    final List<Map<String,Object>> coverage=new ArrayList<>();
    static class Dummy {
        int n,semester;User user;Long session,mentor;String year,branch,company,mode,source,companyType,completion;
        String workflow="NOT_SECURED";boolean profile,active=true,diary;BigDecimal stipend;Boolean offer;InternshipDetails internship;
        List<Day> days=new ArrayList<>();List<Month> months=new ArrayList<>();
        String name(){return String.format("TEST_REPORT_STUDENT_%02d",n);}
        String uid(){return String.format("TEST_REPORT_UID_%02d",n);}
    }
    record Day(long id,LocalDate date,String result,String liveness,boolean deleted) {}
    record Month(long id,int number,LocalDate start,LocalDate end,String status,boolean deleted) {}

    @BeforeAll void seedLocalFixture() throws Exception {
        // Check the actual connection before any fixture write. The demo opt-in is
        // separate from disposable regression schemas and is explicitly local-only.
        try(Connection connection=Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            String url=connection.getMetaData().getURL();
            if(url.startsWith("jdbc:postgresql:")) {
                String schema=System.getenv("EXIF_TEST_DB_SCHEMA");
                boolean demo="true".equals(System.getenv("REPORT_DEMO_RETAIN"));
                String pattern=demo?"ims_report_demo_[0-9a-f]{32}":"ims_deletion_test_[0-9a-f]{32}";
                if(schema==null || !schema.matches(pattern) || !url.matches("jdbc:postgresql://(localhost|127[.]0[.]0[.]1):.*") || !url.contains("currentSchema="+schema))throw new IllegalStateException("A guarded local report test schema is required");
                var identity=jdbc.queryForMap("select current_schema() as schema,host(inet_server_addr()) as address,current_database() as database");
                if(!schema.equals(identity.get("schema")) || !InetAddress.getByName((String)identity.get("address")).isLoopbackAddress())throw new IllegalStateException("Actual report test database is not the expected local schema");
                System.out.println("Verified report fixture target: localhost / "+identity.get("database")+" / "+schema);
            } else if(!url.startsWith("jdbc:h2:mem:report_filter_matrix"))throw new IllegalStateException("Unexpected report fixture database");
        }
        passwordHash=passwords.encode(PASSWORD);
        new TransactionTemplate(transactions).executeWithoutResult(tx->seed());
    }
    private User user(String email,Role role) { return users.saveAndFlush(new User(email,passwordHash,role,true)); }
    private void seed() {
        admin=user("test_report_admin@local.test",Role.ADMIN);mentorA=user("test_report_mentor_a@local.test",Role.MENTOR);mentorB=user("test_report_mentor_b@local.test",Role.MENTOR);
        mentors.saveAndFlush(new MentorProfile(mentorA,"TEST_REPORT_MENTOR_A","TEST_REPORT_MA","Computer Engineering","Professor","9876543210"));
        mentors.saveAndFlush(new MentorProfile(mentorB,"TEST_REPORT_MENTOR_B","TEST_REPORT_MB","Civil","Professor","9876543211"));
        jdbc.update("insert into academic_sessions(name,start_date,end_date,created_by,closed_at,closed_by) values('TEST_REPORT_SESSION_A',DATE '2025-07-01',DATE '2026-06-30',?,current_timestamp,?)",admin.getId(),admin.getId());
        jdbc.update("insert into academic_sessions(name,start_date,end_date,created_by) values('TEST_REPORT_SESSION_B',DATE '2026-07-01',DATE '2027-06-30',?)",admin.getId());
        sessionA=jdbc.queryForObject("select id from academic_sessions where name='TEST_REPORT_SESSION_A'",Long.class);sessionB=jdbc.queryForObject("select id from academic_sessions where name='TEST_REPORT_SESSION_B'",Long.class);
        jdbc.update("update academic_session_settings set active_session_id=?,activated_by=?,activated_at=current_timestamp where id=1",sessionB,admin.getId());
        for(int n=1;n<=60;n++) {
            Dummy d=new Dummy();d.n=n;d.semester=n%8+1;d.session=n==60?null:n%2==1?sessionA:sessionB;
            d.user=new User(String.format("test_report_student_%02d@local.test",n),passwordHash,Role.STUDENT,true);if(d.session!=null)d.user.assignAcademicSession(d.session);users.saveAndFlush(d.user);
            accounts.saveAndFlush(new StudentAccount(d.user,d.name(),d.uid(),d.semester));
            d.profile=n<=18 && n!=2;d.branch=d.profile?(n%2==1?"COMPUTER_ENGINEERING":"CIVIL"):null;d.year=d.profile?(n%2==1?"2026-2027":"2025-2026"):null;
            if(d.profile) {
                var p=new StudentProfile(d.user);p.apply(d.name(),Branch.valueOf(d.branch),d.uid(),"ROLL-"+n,"TEST_REPORT_ADDRESS",null,"personal"+n+"@local.test",d.user.getEmail(),"9876543210","TEST_REPORT_GUARDIAN","9876543210","9876543210","O+","TEST_REPORT_MENTOR","9876543210","TEST_REPORT_COORDINATOR","9876543210","TEST_REPORT_CDC","9876543210",d.year,d.semester);
                p.setEncryptedAadhaar(encryption.encrypt("12345678"+String.format("%04d",n)),String.format("%04d",n));profiles.saveAndFlush(p);
            }
            if(n<=18 && n!=2) {User mentor=n<=12?mentorA:mentorB;d.mentor=mentor.getId();assignments.saveAndFlush(new MentorStudentAssignment(mentor,d.user,admin,NOW));}
            if(n<=17) {
                var o=new InternshipOnboarding(d.user);d.source=n==16?null:List.of("DEPARTMENT","CDC","SELF").get((n-1)%3);if(d.source!=null)o.selectSource(InternshipSource.valueOf(d.source));
                if(n<=13){o.markDocumentsComplete();o.confirmDiary();d.diary=true;}
                if(n==17)o.markDocumentsComplete();
                if(n<=12)o.markInternshipDetailsComplete();onboardings.saveAndFlush(o);d.workflow=o.getWorkflowStatus().name();
                if(n<=3)for(DocumentType type:DocumentType.values()){var document=new InternshipDocument(o,type);document.replace("TEST_REPORT_"+type.name()+".pdf",UUID.randomUUID()+".pdf","application/pdf",100);documents.saveAndFlush(document);}
                if(n<=12) {
                    d.company=n<=6?"TEST_REPORT_COMPANY_ALPHA%_":"TEST_REPORT_COMPANY_BETA";
                    d.mode=List.of("ONLINE","OFFICE_REPORTING","HYBRID","COLLEGE_REPORTING").get((n-1)%4);d.companyType=List.of("IT","MANUFACTURING","CORE","OTHER").get((n-1)%4);
                    d.stipend=n%2==1?new BigDecimal("10000.00"):BigDecimal.ZERO;d.offer=n%3==0;
                    LocalDate start=n<=6?TODAY.minusDays(36):n<=9?TODAY.minusDays(10):TODAY.plusDays(5),end=n<=6?TODAY.minusDays(1):n<=9?TODAY.plusDays(20):TODAY.plusDays(35);
                    d.internship=new InternshipDetails(o);d.internship.apply(d.company,CompanyType.valueOf(d.companyType),d.companyType.equals("OTHER")?"TEST_REPORT_OTHER":null,"TEST_REPORT_WORKPLACE","9876543210","TEST_REPORT_SUPERVISOR","Manager","9876543210",2,start,end,(int)((java.time.temporal.ChronoUnit.DAYS.between(start,end)+7)/7),InternshipMode.valueOf(d.mode),d.stipend,d.offer);internships.saveAndFlush(d.internship);weekly.synchronize(d.internship);
                    d.completion=n<=3?"COMPLETED":n<=6?"COMPLETION_PENDING":n<=9?"IN_PROGRESS":"NOT_STARTED";
                    // Every period is reviewed for officially completed students. Student 2
                    // deliberately has no optional profile, mentor assignment or attendance.
                    if(n<=3) {
                        jdbc.queryForList("select id from weekly_internship_periods where internship_id=?",Long.class,d.internship.getId()).forEach(id->jdbc.update("insert into weekly_progress_reports(period_id,work_description,revision,review_status,submitted_at,reviewer_id,reviewed_at) values(?,'TEST_REPORT_APPROVED_WEEK',1,'APPROVED',?,?,?)",id,Timestamp.from(NOW),mentorA.getId(),Timestamp.from(NOW)));
                        int period=0;for(LocalDate at=start;!at.isAfter(end);at=at.plusMonths(1)) {LocalDate through=at.plusMonths(1).minusDays(1).isBefore(end)?at.plusMonths(1).minusDays(1):end;addMonth(d,++period,at,through,"APPROVED");}
                        var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(UserPrincipal.from(admin),null,UserPrincipal.from(admin).getAuthorities()));SecurityContextHolder.setContext(context);
                        try {assertThat(completions.forAdmin(d.user.getId()).eligible()).isTrue();completions.decide(UserPrincipal.from(admin),d.user.getId(),new InternshipCompletionService.Decision(0,"TEST_REPORT_COMPLETE"),false);}finally{SecurityContextHolder.clearContext();}
                    } else if(n<=9) addMonth(d,1,start,start.plusDays(6),n%3==0?"REJECTED":n%3==1?"PENDING_MENTOR_REVIEW":"APPROVED");
                    if(n!=2 && n<=9) {
                        LocalDate day=n<=6?TODAY.minusDays(1):TODAY;
                        addDay(d,day,n==3?"NOT_APPLICABLE":n==4||n==7?"VERIFIED":n==5||n==8?"FAILED":n==6?"NOT_PERFORMED":"HISTORICAL");
                    }
                    if(n==1)jdbc.update("insert into internship_locations(internship_id,latitude,longitude,geofence_radius_meters,status,created_at,updated_at,confirmed_at,confirmed_by,mentor_approved_by,mentor_reviewed_at,mentor_decision,iiic_decision,workplace_address) values(?,18.5,73.8,200,'APPROVED',?,?,?,?,?,?,'APPROVE','APPROVE','TEST_REPORT_APPROVED_WORKPLACE')",d.internship.getId(),Timestamp.from(NOW),Timestamp.from(NOW),Timestamp.from(NOW),admin.getId(),mentorA.getId(),Timestamp.from(NOW));
                    if(n==4||n==7)jdbc.update("insert into internship_locations(internship_id,latitude,longitude,geofence_radius_meters,status,created_at,updated_at,workplace_address) values(?,18.5,73.8,200,?,?,?,'TEST_REPORT_WORKPLACE')",d.internship.getId(),n==4?"PENDING":"REJECTED",Timestamp.from(NOW),Timestamp.from(NOW));
                }
            }
            if(n==3 || n==60){jdbc.update("update users set active=false where id=?",d.user.getId());d.active=false;}
            students.add(d);
        }
    }
    private void addMonth(Dummy d,int number,LocalDate start,LocalDate end,String status) {
        var m=new MonthlyProgressReport(d.internship,number,start,end,"TEST_REPORT_WORK_"+d.n,"TEST_REPORT_PHOTO_"+d.n+"_"+number,"test-report.png","image/png",100,null,null,null,NOW);
        if(!status.equals("PENDING_MENTOR_REVIEW"))m.decide(mentorA,status.equals("APPROVED"),status.equals("REJECTED")?"TEST_REPORT_CORRECTION":null,NOW);monthly.saveAndFlush(m);d.months.add(new Month(m.getId(),number,start,end,status,false));
    }
    private void addDay(Dummy d,LocalDate day,String liveness) {
        var record=liveness.equals("NOT_APPLICABLE")?AttendanceRecord.automaticAbsent(d.user,d.internship,day,NOW):new AttendanceRecord(d.user,d.internship,day,AttendanceResult.PRESENT,liveness.equals("VERIFIED")?VerificationStatus.VERIFIED:VerificationStatus.UNVERIFIED,AttendanceSubmissionType.BASIC,NOW);
        if(!Set.of("HISTORICAL","NOT_APPLICABLE").contains(liveness))record.useLivenessVerification();attendance.saveAndFlush(record);
        if(Set.of("VERIFIED","FAILED").contains(liveness)) {
            String session=UUID.randomUUID().toString();
            jdbc.update("insert into attendance_verification_sessions(id,student_user_id,internship_id,attendance_date,challenge_type,created_at,expires_at,consumed_at) values(?,?,?,?,'BLINK',?,?,?)",session,d.user.getId(),d.internship.getId(),day,Timestamp.from(NOW),Timestamp.from(NOW.plusSeconds(60)),Timestamp.from(NOW));
            jdbc.update("insert into attendance_evidence(attendance_id,camera_capture_used,verification_reason,image_media_type,image_size_bytes,ai_verification_session_id,ai_face_detected,ai_single_face,ai_quality_passed,ai_liveness_passed,ai_processed_at,ai_failure_reason,created_at) values(?,true,?,'image/jpeg',100,?,true,true,true,?,?,?,?)",record.getId(),liveness.equals("VERIFIED")?"EDGE_AI_VERIFIED":"EDGE_AI_LIVENESS_FAILED",session,liveness.equals("VERIFIED"),Timestamp.from(NOW),liveness.equals("VERIFIED")?null:"EDGE_AI_LIVENESS_FAILED",Timestamp.from(NOW));
        }
        d.days.add(new Day(record.getId(),day,liveness.equals("NOT_APPLICABLE")?"ABSENT":"PRESENT",liveness,false));
    }
    Cookie cookie(User user){return new Cookie("ims_auth",jwt.create(UserPrincipal.from(user)));}
    String base(User user){return "/api/v1/"+(user.getRole()==Role.MENTOR?"mentor":"admin")+"/reports";}
    MvcResult call(User user,String endpoint,Object body) throws Exception {
        return mvc.perform(post(base(user)+endpoint).cookie(cookie(user)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn();
    }
    List<Dummy> authorized(User user){return students.stream().filter(d->user==admin || Objects.equals(d.mentor,user.getId())).toList();}
    JsonNode options(User user) throws Exception{return json.readTree(mvc.perform(get(base(user)+"/types").cookie(cookie(user))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    Map<String,Object> request(String type,Map<String,String> filters,List<Long> ids,int page,String format,List<String> fields){var body=new LinkedHashMap<String,Object>();body.put("reportType",type);body.put("filters",filters);body.put("studentIds",ids);body.put("page",page);body.put("format",format);if(fields!=null)body.put("fields",fields);return body;}
    JsonNode table(User user,String type,Map<String,String> filters,List<Long> ids,int page) throws Exception{return json.readTree(call(user,"/table",request(type,filters,ids,page,"XLSX",null)).getResponse().getContentAsString());}

    @Test void completedStudentsWithMissingOptionalRecordsAppearAndAllDoesNotExcludeAnyStudents() throws Exception {
        var all=table(admin,"ROSTER",Map.of("completionStatus","ALL"),List.of(),0);assertThat(all.path("totalRows").asInt()).isEqualTo(60);
        var completed=table(admin,"COMPLETIONS",Map.of("completionStatus","COMPLETED"),List.of(),0);assertThat(ids(completed)).containsExactlyElementsOf(students.subList(0,3).stream().map(d->d.user.getId()).toList());
        assertThat(students.get(1).profile).isFalse();assertThat(students.get(1).mentor).isNull();assertThat(students.get(1).days).isEmpty();
        assertThat(table(admin,"ROSTER",Map.of("workflowStatus","INTERNSHIP_DETAILS_COMPLETE"),List.of(),0).path("totalRows").asInt()).isEqualTo(12);
    }
    static List<Long> ids(JsonNode response){List<Long> ids=new ArrayList<>();response.path("rows").forEach(row->ids.add(row.path("studentId").asLong()));return ids;}
    record FilterCase(String id,Map<String,String> request,Map<String,String> expected) {}
    List<FilterCase> cases(User viewer,JsonNode filters) {
        List<FilterCase> cases=new ArrayList<>();cases.add(new FilterCase("NO_FILTERS",Map.of(),Map.of()));
        for(var filter:filters) {
            String id=filter.path("id").asText();List<String> values=new ArrayList<>();
            if(filter.path("type").asText().equals("select"))filter.path("choices").forEach(c->values.add(c.path("value").asText()));
            else values.addAll(switch(id) {
                case "search" -> List.of("TEST_REPORT_STUDENT_01","test_report_uid_02","%","_","NO_SUCH_STUDENT");
                case "completionSearch" -> List.of("TEST_REPORT_STUDENT_01","test_report_uid_02","alpha%_","NO_SUCH_COMPANY");
                case "company" -> List.of("alpha%_","beta","NO_SUCH_COMPANY");
                case "academicYear" -> List.of("2026-2027","2025-2026","2099-2100");
                case "studentId" -> List.of(authorized(viewer).get(0).user.getId().toString());
                case "mentorId" -> List.of(mentorA.getId().toString(),mentorB.getId().toString());
                case "attendanceDate","historyFrom","historyTo" -> List.of(TODAY.toString(),TODAY.minusDays(1).toString(),TODAY.minusDays(10).toString());
                default -> throw new AssertionError("Available filter has no test cases: "+id);
            });
            for(String value:values) {
                cases.add(new FilterCase(id,Map.of(id,value),value.equals("ALL")?Map.of():Map.of(id,value)));
                if(filter.path("type").asText().equals("select")&&!value.equals("ALL"))cases.add(new FilterCase(id+"_NORMALIZED",Map.of(id," "+value.toLowerCase(Locale.ROOT)+" "),Map.of(id,value)));
            }
            cases.add(new FilterCase(id+"_CLEARED",Map.of(id," "),Map.of()));
            if(filter.path("type").asText().equals("select"))cases.add(new FilterCase(id+"_ALL",Map.of(id,"ALL"),Map.of()));
        }
        return cases;
    }
    boolean matches(Dummy d,Map<String,String> filters,boolean institutional) {
        for(var e:filters.entrySet()) {
            String value=e.getValue(),id=e.getKey();if(value.isBlank()||value.equals("ALL"))continue;
            boolean match=switch(id) {
                case "search" -> contains(d.name(),value)||contains(d.uid(),value)||contains(d.user.getEmail(),value);
                case "completionSearch" -> contains(d.name(),value)||contains(d.uid(),value)||contains(d.company,value);
                case "studentId" -> d.user.getId().equals(Long.valueOf(value));
                case "academicSessionId" -> Objects.equals(d.session,Long.valueOf(value));
                case "academicYear" -> Objects.equals(d.year,value);
                case "branch" -> Objects.equals(d.branch,value);
                case "semester" -> d.semester==Integer.parseInt(value);
                case "profileStatus" -> d.profile==value.equals("COMPLETE");
                case "workflowStatus" -> d.workflow.equals(value);
                case "completionStatus" -> Objects.equals(d.completion,value);
                case "company" -> contains(d.company,value);
                case "internshipMode" -> Objects.equals(d.mode,value);
                case "internshipSource" -> Objects.equals(d.source,value);
                case "companyType" -> Objects.equals(d.companyType,value);
                case "mentorId" -> Objects.equals(d.mentor,Long.valueOf(value));
                case "mentorAssignment" -> (d.mentor!=null)==value.equals("ASSIGNED");
                case "paid" -> d.stipend!=null && (value.equals("PAID")?d.stipend.signum()>0:d.stipend.signum()==0);
                case "fullTimeOffered" -> d.offer!=null && d.offer==value.equals("YES");
                case "accountActive" -> d.active==value.equals("YES");
                case "attendanceStatus","verificationStatus" -> institutional || dayValue(d,filters.getOrDefault("attendanceDate",TODAY.toString()),id).equals(value);
                case "attendanceDate","historyFrom","historyTo","monthlyStatus" -> true;
                default -> throw new AssertionError("Unhandled filter "+id);
            };if(!match)return false;
        }return true;
    }
    static boolean contains(String text,String value){return text!=null&&text.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT));}
    String dayValue(Dummy d,String reference,String field){return d.days.stream().filter(day->!day.deleted()&&day.date().equals(LocalDate.parse(reference))).map(day->field.equals("attendanceStatus")?day.result():day.liveness()).findFirst().orElse("NOT_SUBMITTED");}
    List<Dummy> filtered(User viewer,Map<String,String> filters,List<Long> selected,boolean institutional){return authorized(viewer).stream().filter(d->selected.isEmpty()||selected.contains(d.user.getId())).filter(d->matches(d,filters,institutional)).toList();}
    boolean dayMatches(Day day,Map<String,String> filters){return !day.deleted()&&(!filters.containsKey("historyFrom")||!day.date().isBefore(LocalDate.parse(filters.get("historyFrom"))))&&(!filters.containsKey("historyTo")||!day.date().isAfter(LocalDate.parse(filters.get("historyTo"))))&&(!filters.containsKey("attendanceStatus")||day.result().equals(filters.get("attendanceStatus")))&&(!filters.containsKey("verificationStatus")||day.liveness().equals(filters.get("verificationStatus")));}
    Map<String,Object> identity(Dummy d){var row=new LinkedHashMap<String,Object>();row.put("studentId",d.user.getId());row.put("uid",d.uid());row.put("studentName",d.name());row.put("company",d.company);row.put("workflow",d.workflow);return row;}
    static long count(List<Dummy> rows,Predicate<Dummy> predicate){return rows.stream().filter(predicate).count();}
    static String source(Dummy d){return d.source==null?"UNRECORDED":d.source;}
    void sourceCounts(Map<String,Object> row,List<Dummy> group,Predicate<Dummy> predicate){row.put("department",count(group,d->source(d).equals("DEPARTMENT")&&predicate.test(d)));row.put("cdc",count(group,d->source(d).equals("CDC")&&predicate.test(d)));row.put("self",count(group,d->source(d).equals("SELF")&&predicate.test(d)));row.put("unknown",count(group,d->source(d).equals("UNRECORDED")&&predicate.test(d)));row.put("total",count(group,predicate));}
    List<Map<String,Object>> expected(String type,List<Dummy> scope,Map<String,String> filters) {
        List<Map<String,Object>> rows=new ArrayList<>();
        if(Set.of("ROSTER","COMPLETIONS","ATTENDANCE","ATTENDANCE_SUMMARY","MONTHLY_PROGRESS").contains(type)) {
            for(Dummy d:scope) {
                if(type.equals("ROSTER")){rows.add(identity(d));continue;}
                if(type.equals("COMPLETIONS")){if(d.internship!=null){var row=identity(d);row.put("completionStatus",d.completion);rows.add(row);}continue;}
                if(type.equals("MONTHLY_PROGRESS")){for(Month m:d.months)if(!m.deleted()&&(!filters.containsKey("monthlyStatus")||m.status().equals(filters.get("monthlyStatus")))){var row=identity(d);row.put("recordId",m.id());row.put("monthNumber",m.number());row.put("reviewStatus",m.status());rows.add(row);}continue;}
                var days=d.days.stream().filter(day->dayMatches(day,filters)).toList();
                if(type.equals("ATTENDANCE")){for(Day day:days){var row=identity(d);row.put("recordId",day.id());row.put("date",day.date().toString());row.put("result",day.result());row.put("verification",day.liveness());rows.add(row);}continue;}
                var row=identity(d);row.put("total",days.size());
                row.put("present",days.stream().filter(day->day.result().equals("PRESENT")).count());row.put("absent",days.stream().filter(day->day.result().equals("ABSENT")).count());
                for(var value:Map.of("verified","VERIFIED","unverified","FAILED","notPerformed","NOT_PERFORMED","historical","HISTORICAL").entrySet())row.put(value.getKey(),days.stream().filter(day->day.liveness().equals(value.getValue())).count());
                rows.add(row);
            }return rows;
        }
        if(type.equals("COMPARISON")) {
            Map<String,Predicate<Dummy>> comparisons=new LinkedHashMap<>();comparisons.put("Total Students",d->true);comparisons.put("Stipend Students",d->d.stipend!=null&&d.stipend.signum()>0);comparisons.put("Non-Stipend Students",d->d.stipend!=null&&d.stipend.signum()==0);comparisons.put("Placement Offers",d->Boolean.TRUE.equals(d.offer));comparisons.put("Diary Issued",d->d.diary);comparisons.put("Details Complete",d->d.workflow.equals("INTERNSHIP_DETAILS_COMPLETE"));
            comparisons.forEach((key,predicate)->{Map<String,Object> row=new LinkedHashMap<>();row.put("parameter",key);sourceCounts(row,scope,predicate);rows.add(row);});return rows;
        }
        boolean company=Set.of("COMPANY","COMPANY_STIPEND","PLACEMENT_COMPANY").contains(type),distribution=type.equals("STIPEND_DISTRIBUTION");
        Map<String,List<Dummy>> groups=new TreeMap<>();for(Dummy d:scope){if(company&&d.company==null||distribution&&d.stipend==null)continue;String key=company?d.company:distribution?d.stipend.stripTrailingZeros().toPlainString():type.equals("WORKFLOW")?d.workflow:source(d);groups.computeIfAbsent(key,k->new ArrayList<>()).add(d);}
        groups.forEach((key,group)->{
            Map<String,Object> row=new LinkedHashMap<>();row.put(company?"company":distribution?"stipend":"category",distribution?new BigDecimal(key):key);row.put("total",group.size());
            switch(type) {
                case "OVERALL" -> row.put("percentage",100.0*group.size()/scope.size());
                case "STIPEND","COMPANY_STIPEND" -> {row.put("paid",count(group,d->d.stipend!=null&&d.stipend.signum()>0));row.put("unpaid",count(group,d->d.stipend!=null&&d.stipend.signum()==0));if(type.equals("STIPEND"))row.put("unknown",count(group,d->d.stipend==null));else row.put("offers",count(group,d->Boolean.TRUE.equals(d.offer)));}
                case "COMPANY" -> sourceCounts(row,group,d->true);
                case "PLACEMENT","PLACEMENT_COMPANY" -> {row.put("offers",count(group,d->Boolean.TRUE.equals(d.offer)));row.put("noOffers",count(group,d->Boolean.FALSE.equals(d.offer)));if(type.equals("PLACEMENT"))row.put("unknown",count(group,d->d.offer==null));}
                case "DIARY_ISSUANCE" -> {row.put("issued",count(group,d->d.diary));row.put("notIssued",count(group,d->!d.diary));}
                case "WORKFLOW","STIPEND_DISTRIBUTION" -> {}
                default -> throw new AssertionError("Unhandled available report: "+type);
            }rows.add(row);
        });return rows;
    }
    String key(String type,Map<String,Object> row){if(row.containsKey("recordId"))return "R"+((Number)row.get("recordId")).longValue();if(row.containsKey("studentId"))return "S"+((Number)row.get("studentId")).longValue();String field=type.equals("COMPARISON")?"parameter":type.startsWith("COMPANY")||type.equals("PLACEMENT_COMPANY")?"company":type.equals("STIPEND_DISTRIBUTION")?"stipend":"category";Object value=row.get(field);return value instanceof Number?new BigDecimal(value.toString()).stripTrailingZeros().toPlainString():String.valueOf(value);}
    void assertValue(Object expected,Object actual,String context){if(expected==null){assertThat(actual).as(context).isNull();return;}if(expected instanceof Number){assertThat(actual).as(context).isInstanceOf(Number.class);assertThat(((Number)actual).doubleValue()).as(context).isCloseTo(((Number)expected).doubleValue(),within(0.00011));}else assertThat(String.valueOf(actual)).as(context).isEqualTo(String.valueOf(expected));}
    List<Map<String,Object>> allRows(User viewer,String type,Map<String,String> filters,List<Long> selected,List<Map<String,Object>> expected) throws Exception {
        List<Map<String,Object>> actual=new ArrayList<>();long total=-1;int page=0;
        do {var response=table(viewer,type,filters,selected,page++);if(total<0)total=response.path("totalRows").asLong();assertThat(response.path("totalRows").asLong()).isEqualTo(total);for(var row:response.path("rows"))actual.add(json.convertValue(row,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}));if(page>100)throw new AssertionError("Pagination did not finish");}while(actual.size()<total);
        assertThat(actual).as(type+" "+filters).hasSize(expected.size());assertThat(total).isEqualTo(expected.size());
        Map<String,Map<String,Object>> byKey=new LinkedHashMap<>();actual.forEach(row->assertThat(byKey.put(key(type,row),row)).as("No duplicate report row").isNull());
        for(var row:expected){var found=byKey.get(key(type,row));assertThat(found).as(type+" missing "+key(type,row)+" "+filters).isNotNull();for(var e:row.entrySet())assertValue(e.getValue(),found.get(e.getKey()),type+" "+e.getKey()+" "+filters);}
        return actual;
    }
    @Test void everyDashboardFilterValueAndClearedOrAllSelectionMatchesTheLocalDataset() throws Exception {
        for(User viewer:List.of(admin,mentorA,mentorB)) {
            var opts=json.readTree(mvc.perform(get(base(viewer)+"/options").cookie(cookie(viewer))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            for(var c:cases(viewer,opts.path("filters"))) {
                var expected=filtered(viewer,c.expected(),List.of(),false);
                var response=json.readTree(call(viewer,"/preview",Map.of("fields",List.of("studentId","uid","studentName"),"filters",c.request(),"format","XLSX")).getResponse().getContentAsString());
                assertThat(response.path("studentCount").asLong()).as("Dashboard "+viewer.getRole()+" "+c).isEqualTo(expected.size());
                List<Long> actual=new ArrayList<>();response.path("students").forEach(row->actual.add(row.path("studentId").asLong()));assertThat(actual).containsExactlyElementsOf(expected.stream().limit(20).map(d->d.user.getId()).toList());
                coverage.add(Map.of("scope",viewer.getRole()+":"+viewer.getId(),"report","DASHBOARD_EXPORT","filter",c.id(),"request",c.request(),"expected",expected.size(),"actual",response.path("studentCount").asLong()));
            }
        }
    }
    @Test void everyAvailableInstitutionalReportAndEveryApplicableFilterValueReturnsExactlyTheExpectedRecords() throws Exception {
        for(User viewer:List.of(admin,mentorA,mentorB)) {
            var opts=options(viewer);
            for(var report:opts.path("types")) {
                if(!report.path("available").asBoolean())continue;String type=report.path("id").asText();
                for(var c:cases(viewer,opts.path("filters"))) {
                    String id=c.id().replace("_NORMALIZED","").replace("_CLEARED","").replace("_ALL","");
                    if(!type.startsWith("ATTENDANCE")&&Set.of("historyFrom","historyTo","attendanceStatus","verificationStatus").contains(id)||!type.equals("MONTHLY_PROGRESS")&&id.equals("monthlyStatus"))continue;
                    var scope=filtered(viewer,c.expected(),List.of(),true);var rows=expected(type,scope,c.expected());allRows(viewer,type,c.request(),List.of(),rows);
                    checkCsv(viewer,type,c.request(),List.of(),rows);
                    coverage.add(Map.of("scope",viewer.getRole()+":"+viewer.getId(),"report",type,"filter",c.id(),"request",c.request(),"expected",rows.size(),"actual",rows.size(),"export","CSV"));
                }
            }
        }
    }
    List<String> exportFields(String type,List<Map<String,Object>> expected) {
        if(!expected.isEmpty())return new ArrayList<>(expected.get(0).keySet());
        return switch(type){case "ROSTER","COMPLETIONS","ATTENDANCE_SUMMARY"->List.of("studentId","uid","studentName");case "ATTENDANCE","MONTHLY_PROGRESS"->List.of("studentId","recordId");case "COMPARISON"->List.of("parameter","total");case "STIPEND_DISTRIBUTION"->List.of("stipend","total");case "COMPANY","COMPANY_STIPEND","PLACEMENT_COMPANY"->List.of("company","total");default->List.of("category","total");};
    }
    List<List<String>> csv(byte[] bytes) {
        String text=new String(bytes,StandardCharsets.UTF_8).replaceFirst("^\\uFEFF","");List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder value=new StringBuilder();boolean quoted=false;
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c=='"'){if(quoted&&i+1<text.length()&&text.charAt(i+1)=='"'){value.append('"');i++;}else quoted=!quoted;}else if(c==','&&!quoted){row.add(value.toString());value.setLength(0);}else if(c=='\n'&&!quoted){row.add(value.toString());value.setLength(0);rows.add(row);row=new ArrayList<>();}else if(c!='\r'||quoted)value.append(c);}
        if(!value.isEmpty()||!row.isEmpty()){row.add(value.toString());rows.add(row);}return rows;
    }
    void assertExportRows(String type,List<String> fields,List<List<String>> actual,List<Map<String,Object>> expected,String format) {
        assertThat(actual).as(type+" "+format).hasSize(expected.size());Map<String,Map<String,Object>> expectedByKey=new HashMap<>();expected.forEach(row->expectedByKey.put(key(type,row),row));Set<String> seen=new HashSet<>();
        for(var values:actual){Map<String,Object> projected=new LinkedHashMap<>();for(int i=0;i<fields.size();i++)projected.put(fields.get(i),values.get(i));
            String keyField=fields.contains("recordId")?"recordId":fields.contains("studentId")?"studentId":null;
            if(keyField!=null)projected.put(keyField,new BigDecimal(values.get(fields.indexOf(keyField))).longValueExact());
            if(type.equals("STIPEND_DISTRIBUTION"))projected.put("stipend",new BigDecimal(values.get(fields.indexOf("stipend"))));
            String key=key(type,projected);assertThat(seen.add(key)).as("No duplicate exported record").isTrue();var row=expectedByKey.get(key);assertThat(row).as(type+" "+format+" unexpected "+key).isNotNull();
            for(String field:fields){Object expectedValue=row.get(field);String actualValue=values.get(fields.indexOf(field));if(expectedValue==null)assertThat(actualValue).isEmpty();else if(expectedValue instanceof Number)assertThat(Double.parseDouble(actualValue.replace(",",""))).as(type+" "+format+" "+field).isCloseTo(((Number)expectedValue).doubleValue(),within(format.equals("XLSX")?0.051:0.00011));else assertThat(actualValue).as(type+" "+format+" "+field).isEqualTo(expectedValue.toString());}
        }
    }
    void checkCsv(User viewer,String type,Map<String,String> filters,List<Long> selected,List<Map<String,Object>> expected) throws Exception {
        var fields=exportFields(type,expected);var result=call(viewer,"/generate",request(type,filters,selected,0,"CSV",fields));assertThat(result.getResponse().getHeader("X-Report-Student-Count")).isEqualTo(Integer.toString(expected.size()));var records=csv(result.getResponse().getContentAsByteArray());assertThat(records.get(0)).hasSize(fields.size());assertExportRows(type,fields,records.subList(1,records.size()),expected,"CSV");
    }
    @Test void everyAvailableReportExportsTheSameRecordsToExcelCsvAndPdfForBothRoles() throws Exception {
        for(User viewer:List.of(admin,mentorA,mentorB))for(var report:options(viewer).path("types")) {
            if(!report.path("available").asBoolean())continue;String type=report.path("id").asText();var expected=expected(type,authorized(viewer),Map.of());var fields=exportFields(type,expected);
            checkCsv(viewer,type,Map.of(),List.of(),expected);
            for(String format:List.of("XLSX","PDF")) {
                // Keep identity cells unwrapped for text-based PDF record parity.
                // Wide-column wrapping and page continuation have separate writer tests.
                var selectedFields=format.equals("PDF")&&fields.contains("uid")?List.of("studentId","uid"):fields;
                var result=call(viewer,"/generate",request(type,Map.of(),List.of(),0,format,selectedFields));assertThat(result.getResponse().getHeader("X-Report-Student-Count")).isEqualTo(Integer.toString(expected.size()));
                byte[] bytes=result.getResponse().getContentAsByteArray();
                if(format.equals("XLSX"))try(var book=WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
                    var sheet=book.getSheet("Students");List<List<String>> rows=new ArrayList<>();DataFormatter formatter=new DataFormatter(Locale.ROOT);
                    int dataEnd=3+expected.size();
                    assertThat(sheet.getLastRowNum()).isBetween(dataEnd,dataEnd+1);
                    if(sheet.getLastRowNum()>dataEnd) {
                        // Historical Excel templates have a separate totals footer.
                        var total=sheet.getRow(dataEnd+1);assertThat(formatter.formatCellValue(total.getCell(0))).isEqualTo("Total");
                        for(int column=1;column<fields.size();column++)if(total.getCell(column).getCellType()==CellType.NUMERIC) {
                            String field=fields.get(column);double sum=expected.stream().map(row->row.get(field)).filter(Number.class::isInstance).mapToDouble(value->((Number)value).doubleValue()).sum();
                            assertThat(total.getCell(column).getNumericCellValue()).as(type+" Excel total "+field).isCloseTo(sum,within(0.00011));
                        }
                    }
                    for(int index=4;index<=dataEnd;index++){List<String> row=new ArrayList<>();for(int c=0;c<fields.size();c++){
                        Cell cell=sheet.getRow(index).getCell(c);
                        row.add(cell!=null&&cell.getCellType()==CellType.NUMERIC
                                ?DateUtil.isCellDateFormatted(cell)?cell.getLocalDateTimeCellValue().toLocalDate().toString():Double.toString(cell.getNumericCellValue())
                                :formatter.formatCellValue(cell));
                    }rows.add(row);}assertExportRows(type,fields,rows,expected,"XLSX");assertThat(book.getSheet("Report Details")).isNotNull();assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
                } else try(var pdf=PDDocument.load(bytes)) {
                    String text=new PDFTextStripper().getText(pdf),compact=text.replaceAll("\\s+","");assertThat(text).contains(report.path("title").asText(),"Report details");
                    for(var row:expected)if(row.containsKey("uid"))assertThat(compact).contains(row.get("uid").toString());
                }
            }
            coverage.add(Map.of("scope",viewer.getRole()+":"+viewer.getId(),"report",type,"filter","ALL_FORMATS","expected",expected.size(),"formats",List.of("XLSX","CSV","PDF")));
        }
    }
    @Test void combinationsClearingSelectionAndPaginationMatchAllFilteredRowsAndExports() throws Exception {
        List<Map<String,String>> combinations=List.of(
                Map.of("academicSessionId",sessionA.toString(),"company","alpha%_","mentorId",mentorA.getId().toString(),"completionStatus","COMPLETED","paid","PAID"),
                Map.of("branch","CIVIL","semester","5","company","alpha%_","workflowStatus","INTERNSHIP_DETAILS_COMPLETE"),
                Map.of("profileStatus","INCOMPLETE","mentorAssignment","UNASSIGNED"),Map.of("academicSessionId",sessionB.toString(),"academicYear","2026-2027"),
                Map.of("company","beta","internshipMode","HYBRID","fullTimeOffered","YES"),Map.of("search","NO_SUCH_STUDENT","accountActive","NO"));
        for(var filters:combinations)for(String type:List.of("ROSTER","COMPLETIONS","OVERALL","COMPANY","WORKFLOW","MONTHLY_PROGRESS","ATTENDANCE","ATTENDANCE_SUMMARY")) {
            var scope=filtered(admin,filters,List.of(),true);var expected=expected(type,scope,filters);allRows(admin,type,filters,List.of(),expected);checkCsv(admin,type,filters,List.of(),expected);
            coverage.add(Map.of("scope","ADMIN","report",type,"filter","COMBINED","request",filters,"expected",expected.size()));
        }
        List<Long> selected=List.of(students.get(0).user.getId(),students.get(1).user.getId(),students.get(6).user.getId());
        for(String type:List.of("ROSTER","COMPLETIONS","ATTENDANCE_SUMMARY")){var expected=expected(type,filtered(admin,Map.of(),selected,true),Map.of());allRows(admin,type,Map.of(),selected,expected);checkCsv(admin,type,Map.of(),selected,expected);}
        assertThat(table(admin,"ROSTER",Map.of(),List.of(),0).path("rows")).hasSize(50);assertThat(table(admin,"ROSTER",Map.of(),List.of(),1).path("rows")).hasSize(10);assertThat(table(admin,"ROSTER",Map.of(),List.of(),2).path("rows")).isEmpty();
        allRows(admin,"ROSTER",Map.of(),List.of(),expected("ROSTER",students,Map.of()));checkCsv(admin,"ROSTER",Map.of(),List.of(),expected("ROSTER",students,Map.of()));
        for(String status:List.of("VERIFIED","FAILED","HISTORICAL","NOT_PERFORMED","NOT_APPLICABLE"))for(String type:List.of("ATTENDANCE","ATTENDANCE_SUMMARY")) {
            var filters=Map.of("historyFrom",TODAY.minusDays(1).toString(),"historyTo",TODAY.toString(),"verificationStatus",status);var expected=expected(type,students,filters);allRows(admin,type,filters,List.of(),expected);checkCsv(admin,type,filters,List.of(),expected);
        }
        assertThat(table(admin,"COMPLETIONS",Map.of("completionStatus","COMPLETED"),List.of(),0).path("summary").path("completedInternships").asInt()).isEqualTo(3);
        assertThat(table(admin,"COMPLETIONS",Map.of(),List.of(),0).path("summary").path("completionPending").asInt()).isEqualTo(3);
    }
    @Test void authorizationAndInvalidOrUnavailableFiltersCannotBeBypassed() throws Exception {
        for(var report:options(admin).path("types"))if(!report.path("available").asBoolean())mvc.perform(post(base(admin)+"/table").cookie(cookie(admin)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request(report.path("id").asText(),Map.of(),List.of(),0,"XLSX",null)))).andExpect(status().isBadRequest());
        for(String filter:List.of("workplaceApprovalStatus","startDate","endDate","department","unknownFilter"))mvc.perform(post(base(admin)+"/table").cookie(cookie(admin)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request("ROSTER",Map.of(filter,"anything"),List.of(),0,"XLSX",null)))).andExpect(status().isBadRequest());
        for(String endpoint:List.of("/table","/generate")) {
            var body=request("ROSTER",Map.of("company","NO_MATCH"),List.of(students.get(1).user.getId()),0,"PDF",null);
            mvc.perform(post(base(mentorA)+endpoint).cookie(cookie(mentorA)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isForbidden());
            mvc.perform(post(base(admin)+endpoint).cookie(cookie(students.get(0).user)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isForbidden());
            mvc.perform(post(base(admin)+endpoint).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isUnauthorized());
        }
        for(var filter:List.of(Map.of("historyFrom","2026-11-01","historyTo","2026-10-01"),Map.of("historyFrom","1000-01-01"),Map.of("verificationStatus","INVALID")))mvc.perform(post(base(admin)+"/table").cookie(cookie(admin)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request("ATTENDANCE",filter,List.of(),0,"XLSX",null)))).andExpect(status().isBadRequest());
    }
    @AfterAll void writeCoverage() throws Exception {
        String path=System.getProperty("report.matrix.output");if(path!=null){Path output=Path.of(path);Files.createDirectories(output.getParent());json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(),Map.of("primaryDummyStudents",60,"checks",coverage.size(),"results",coverage));}
    }
    @Test void selectedIdsPaidAndOfferFiltersAreIncludedInCompletionSummaryCounts() throws Exception {
        var t=table(admin,"COMPLETIONS",Map.of("paid","PAID"),List.of(students.get(0).user.getId()),0);
        assertThat(t.path("totalRows").asInt()).isEqualTo(1);assertThat(t.path("summary").path("totalStudents").asInt()).isEqualTo(1);assertThat(t.path("summary").path("completedInternships").asInt()).isEqualTo(1);
    }
    @Test void sessionChoicesUseRealAssignedIdsAndIncludeHistoricalSessions() throws Exception {
        for(User viewer:List.of(admin,mentorA,mentorB)) {
            var filter=options(viewer).path("filters").findValues("id");assertThat(filter).isNotEmpty();
            var session=options(viewer).path("filters");JsonNode actual=null;for(var f:session)if(f.path("id").asText().equals("academicSessionId"))actual=f;
            assertThat(actual).isNotNull();assertThat(actual.path("type").asText()).isEqualTo("select");assertThat(actual.path("choices").toString()).contains("TEST_REPORT_SESSION_A","TEST_REPORT_SESSION_B");
            assertThat(table(viewer,"ROSTER",Map.of("academicSessionId",sessionA.toString()),List.of(),0).path("totalRows").asInt()).isEqualTo(authorized(viewer).stream().filter(d->Objects.equals(d.session,sessionA)).count());
        }
    }
    @TestConfiguration static class FixedClock {@Bean @Primary Clock reportClock(){return Clock.fixed(NOW,ZoneId.of("Asia/Kolkata"));}}
}
