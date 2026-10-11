package edu.institution.ims.monthlyprogress;
import edu.institution.ims.attendance.*;
import edu.institution.ims.auth.LoginRequest;
import edu.institution.ims.internship.storage.FileStorageService;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.deletion.MentorRecordDeletionService;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MentorRecordDeletionIntegrationTest extends MonthlyProgressIntegrationTest {
 @Autowired JdbcTemplate jdbc;
 @Autowired AttendanceRepository attendance;
 @Autowired FileStorageService storage;
 @Autowired MentorRecordDeletionService deletion;
 @AfterEach void removeRetainedTestRecords() {
  jdbc.update("delete from mentor_record_deletion_audit"); jdbc.update("delete from attendance_evidence"); jdbc.update("delete from attendance_verification_sessions"); jdbc.update("delete from attendance_records");
  for(var key:jdbc.queryForList("select distinct photo_storage_key from monthly_progress_reports",String.class)) storage.delete(key);
  jdbc.update("delete from monthly_progress_reports");
 }
 Cookie session(edu.institution.ims.user.User u,String password) throws Exception {
  return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new LoginRequest(u.getEmail(),password)))).andExpect(status().isOk()).andReturn().getResponse().getCookie("ims_auth");
 }
 long attendanceRecord(boolean evidence) {
  var internship=internships.findByOnboardingStudentId(studentA.getId()).orElseThrow();
  var record=attendance.saveAndFlush(new AttendanceRecord(studentA,internship,LocalDate.of(2026,11,10),AttendanceResult.PRESENT,VerificationStatus.UNVERIFIED,AttendanceSubmissionType.BASIC,NOW));
  if(evidence) {
   jdbc.update("insert into attendance_verification_sessions(id,student_user_id,internship_id,attendance_date,challenge_type,created_at,expires_at,consumed_at) values ('deletion-test-session',?,?,?,'BLINK',?,?,?)",studentA.getId(),internship.getId(),record.getAttendanceDate(),Timestamp.from(NOW),Timestamp.from(NOW.plusSeconds(300)),Timestamp.from(NOW));
   jdbc.update("insert into attendance_evidence(attendance_id,camera_capture_used,verification_reason,image_media_type,image_size_bytes,created_at,ai_verification_session_id) values (?,true,'LIVE_LOCATION_UNAVAILABLE','image/jpeg',100,?,'deletion-test-session')",record.getId(),Timestamp.from(NOW));
  } return record.getId();
 }
 long monthlyRecord(int month,String key) {
  var internship=internships.findByOnboardingStudentId(studentA.getId()).orElseThrow();
  return reports.saveAndFlush(new MonthlyProgressReport(internship,month,LocalDate.of(2026,10,17),LocalDate.of(2026,11,16),"Deletion test",key,"photo.png","image/png",100,null,null,null,NOW)).getId();
 }
 String reason() {return "{\"reason\":\"Incorrect submission selected for removal\"}";}
 void remove(String kind,long id,Cookie cookie,int expected) throws Exception {
  mvc.perform(delete("/api/v1/mentor/"+kind+"/"+id).cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(reason())).andExpect(status().is(expected));
 }
 @Test void assignedMentorDeletesAttendancePreservingEvidenceSessionAndAllowsMultipleReplacements() throws Exception {
  long id=attendanceRecord(true); var mentor=session(mentorA,"MentorPassword!23"); remove("attendance",id,mentor,204);
  assertThat(attendance.findById(id)).isEmpty();
  assertThat(jdbc.queryForObject("select count(*) from attendance_evidence where attendance_id=?",Integer.class,id)).isEqualTo(1);
  assertThat(jdbc.queryForObject("select consumed_at from attendance_verification_sessions where id='deletion-test-session'",Timestamp.class)).isNotNull();
  var audit=jdbc.queryForMap("select * from mentor_record_deletion_audit where record_id=?",id);
  assertThat(((Number)audit.get("deleting_user_id")).longValue()).isEqualTo(mentorA.getId()); assertThat(audit.get("deleted_at")).isNotNull(); assertThat(audit.get("reason")).isEqualTo("Incorrect submission selected for removal");
  mvc.perform(get("/api/v1/mentor/attendance/today").cookie(mentor)).andExpect(status().isOk()).andExpect(jsonPath("$[0].todayAttendance").value(org.hamcrest.Matchers.nullValue()));
  long replacement=attendanceRecord(false); assertThat(replacement).isNotEqualTo(id); remove("attendance",replacement,mentor,204); attendanceRecord(false);
 }
 @Test void assignedMentorDeletesMonthlyReportPreservingPhotoAndAllowsReplacement() throws Exception {
  String key=storage.store(new ByteArrayInputStream(new byte[]{1,2,3}),"png"); long id=monthlyRecord(1,key); var mentor=session(mentorA,"MentorPassword!23"); remove("monthly-progress",id,mentor,204);
  assertThat(reports.findById(id)).isEmpty(); assertThat(storage.load(key)).isNotNull();
  mvc.perform(get("/api/v1/mentor/monthly-progress/"+id+"/photo").cookie(mentor)).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/admin/monthly-progress").cookie(session(admin,"AdminPassword!23"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
  assertThat(jdbc.queryForObject("select record_type from mentor_record_deletion_audit where record_id=?",String.class,id)).isEqualTo("MONTHLY_REPORT"); monthlyRecord(1,key);
 }
 @Test void sharedPhotographRemainsReferencedWhenOneReportIsRemoved() throws Exception {
  String key=storage.store(new ByteArrayInputStream(new byte[]{1,2,3}),"png"); long id=monthlyRecord(1,key); monthlyRecord(2,key); remove("monthly-progress",id,session(mentorA,"MentorPassword!23"),204);
  assertThat(reports.count()).isEqualTo(1); assertThat(storage.load(key)).isNotNull();
 }
 @Test void crossMentorStudentsAdminAndAnonymousCannotDeleteEitherType() throws Exception {
  long aid=attendanceRecord(false),mid=monthlyRecord(1,"00000000-0000-0000-0000-000000000000.png");
  for(var pair:Map.of("attendance",aid,"monthly-progress",mid).entrySet()) {
   remove(pair.getKey(),pair.getValue(),session(mentorB,"MentorPassword!24"),404); remove(pair.getKey(),pair.getValue(),session(studentA,"StudentPassword!23"),403); remove(pair.getKey(),pair.getValue(),session(admin,"AdminPassword!23"),403);
   mvc.perform(delete("/api/v1/mentor/"+pair.getKey()+"/"+pair.getValue()).contentType(MediaType.APPLICATION_JSON).content(reason())).andExpect(status().isUnauthorized());
  }
  assertThat(attendance.findById(aid)).isPresent(); assertThat(reports.findById(mid)).isPresent(); assertThat(jdbc.queryForObject("select count(*) from mentor_record_deletion_audit",Integer.class)).isZero();
 }
 @Test void missingRepeatedDeletionAndInvalidReasonAreSafe() throws Exception {
  var mentor=session(mentorA,"MentorPassword!23"); remove("attendance",99999999,mentor,404); remove("monthly-progress",99999999,mentor,404); long id=attendanceRecord(false);
  mvc.perform(delete("/api/v1/mentor/attendance/"+id).cookie(mentor).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}")).andExpect(status().isBadRequest());
  remove("attendance",id,mentor,204); remove("attendance",id,mentor,404);
 }
 @Test void auditDatabaseFailureRollsBackBothRecordTypesWithoutTouchingPhotos() throws Exception {
  long aid=attendanceRecord(true); String key=storage.store(new ByteArrayInputStream(new byte[]{1,2,3}),"png"); long mid=monthlyRecord(1,key);
  for(var pair:Map.of("ATTENDANCE",aid,"MONTHLY_REPORT",mid).entrySet()) {
   jdbc.update("insert into mentor_record_deletion_audit(record_type,record_id,deleting_user_id,deleted_at,reason) values (?,?,?,?,?)",pair.getKey(),pair.getValue(),mentorA.getId(),Timestamp.from(NOW),"Force unique audit failure");
   remove(pair.getKey().equals("MONTHLY_REPORT") ? "monthly-progress" : "attendance",pair.getValue(),session(mentorA,"MentorPassword!23"),500);
  }
  assertThat(attendance.findById(aid)).isPresent(); assertThat(reports.findById(mid)).isPresent(); assertThat(storage.load(key)).isNotNull();
 }
 @Test void institutionalDashboardExcludesDeletedAttendance() throws Exception {
  long id=attendanceRecord(false); var mentor=session(mentorA,"MentorPassword!23"); remove("attendance",id,mentor,204);
  mvc.perform(post("/api/v1/mentor/reports/table").cookie(mentor).contentType(MediaType.APPLICATION_JSON).content("{\"reportType\":\"ATTENDANCE\",\"fields\":[\"recordId\",\"studentName\",\"date\"],\"filters\":{},\"studentIds\":[],\"page\":0,\"format\":\"XLSX\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.totalRows").value(0));
 }
}
