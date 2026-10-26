package edu.institution.ims.monthlyprogress;
import edu.institution.ims.attendance.*;
import edu.institution.ims.auth.LoginRequest;
import edu.institution.ims.internship.InternshipMode;
import edu.institution.ims.internship.dto.RegisteredWorkplaceRequest;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.User;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SharedWorkplaceIntegrationTest extends MonthlyProgressIntegrationTest {
 @Autowired InternshipLocationService locationService; @Autowired InternshipLocationRepository locations;
 @Autowired PhysicalVerificationPolicy policy; @Autowired JdbcTemplate jdbc;
 @Autowired LateAttendancePermissionService latePermissions;
 @AfterEach void cleanWorkplaces(){jdbc.update("delete from attendance_submission_audit");jdbc.update("delete from late_attendance_permission_audit");jdbc.update("delete from late_attendance_permissions");jdbc.update("delete from workplace_review_audit");jdbc.update("delete from attendance_evidence");jdbc.update("delete from attendance_records");jdbc.update("delete from attendance_verification_sessions");jdbc.update("delete from workplace_correction_audit"); locations.deleteAll();}
 Cookie login(User u,String password) throws Exception { return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new LoginRequest(u.getEmail(),password)))).andExpect(status().isOk()).andReturn().getResponse().getCookie("ims_auth"); }
 void register(User u,String lat,String lng,String name) {
  locationService.registerSelectedPlace(internships.findByOnboardingStudentId(u.getId()).orElseThrow(),new RegisteredWorkplaceRequest("test-place-"+u.getId(),name+" test address",new BigDecimal(lat),new BigDecimal(lng),name));
 }
 void approve(User student) {
  var mentor=student.getId().equals(studentA.getId()) ? mentorA : mentorB;
  var location=locations.findByInternshipId(internships.findByOnboardingStudentId(student.getId()).orElseThrow().getId()).orElseThrow();
  locationService.review(student.getId(),UserPrincipal.from(mentor),new WorkplaceReview(location.getRevision(),WorkplaceReview.Decision.APPROVE,null));
  locationService.review(student.getId(),UserPrincipal.from(admin),new WorkplaceReview(location.getRevision(),WorkplaceReview.Decision.APPROVE,null));
 }
 long submitPhoto(User u,String password) throws Exception {
  var result=mvc.perform(multipart("/api/v1/student/monthly-progress/1").file(new MockMultipartFile("photo","test.jpg","image/jpeg",ProgressPhotoValidatorTest.fixture(true,true,21))).file(new MockMultipartFile("workDescription","","text/plain","GPS comparison test".getBytes())).cookie(login(u,password))).andExpect(status().isCreated()).andReturn();
  return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
 }
 @Test void differentStudentsResolveDifferentWorkplacesForPhotoAndRetainedLegacyPolicy() throws Exception {
  register(studentA,"21.125","79.0875","Branch A"); register(studentB,"19.076","72.8777","Branch B");
  approve(studentA);approve(studentB);
  var a=locations.findByInternshipId(internships.findByOnboardingStudentId(studentA.getId()).orElseThrow().getId()).orElseThrow();
  var b=locations.findByInternshipId(internships.findByOnboardingStudentId(studentB.getId()).orElseThrow().getId()).orElseThrow();
  var gps=new PhysicalEvidenceInput(new BigDecimal("21.125"),new BigDecimal("79.0875"),new BigDecimal("5"),new ValidatedCameraImage("image/jpeg",100,null,null,null));
  assertThat(policy.evaluate(InternshipMode.OFFICE_REPORTING,a,gps,NOW,locationService.fullyApproved(a)).status()).isEqualTo(VerificationStatus.VERIFIED);
  assertThat(policy.evaluate(InternshipMode.OFFICE_REPORTING,b,gps,NOW,locationService.fullyApproved(b)).reason()).isEqualTo(VerificationReason.OUTSIDE_GEOFENCE);
  long aid=submitPhoto(studentA,"StudentPassword!23"),bid=submitPhoto(studentB,"StudentPassword!24");
  assertThat(reports.findById(aid).orElseThrow().getWorkplaceVerificationStatus()).isNull();
  assertThat(reports.findById(bid).orElseThrow().getWorkplaceVerificationStatus()).isNull();
  mvc.perform(get("/api/v1/mentor/students/"+studentA.getId()+"/internship/location").cookie(login(mentorA,"MentorPassword!23"))).andExpect(status().isOk()).andExpect(jsonPath("$.workplaceName").value("Branch A"));
 }
 @Test void dailyLivenessIsIndependentOfDifferentWorkplacesApprovalsAndPhotoGps() throws Exception {
  register(studentA,"21.125","79.0875","Approved branch");approve(studentA);
  register(studentB,"19.076","72.8777","Unapproved different branch");
  for(var student:new User[]{studentA,studentB}) {
   // The shared monthly-report fixture is exactly at noon, so use the existing mentor authorization.
   latePermissions.setForMentor(student.getId(),true,UserPrincipal.from(student==studentA?mentorA:mentorB));
   var cookie=login(student,student==studentA?"StudentPassword!23":"StudentPassword!24");
   var issued=json.readTree(mvc.perform(post("/api/v1/student/attendance/verification-session").cookie(cookie))
    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
   var ai=new MockMultipartFile("aiEvidence","ai-evidence.json","application/json",json.writeValueAsBytes(java.util.Map.of(
    "sessionId",issued.get("sessionId").asText(),"challenge",issued.get("challenge").asText(),"faceCount",1,
    "qualityPassed",true,"livenessPassed",true,"modelVersion","mediapipe-face-landmarker@1.0.1/test")));
   mvc.perform(multipart("/api/v1/student/attendance/today/physical")
    .file(new MockMultipartFile("photo","capture.jpg","image/jpeg",ProgressPhotoValidatorTest.fixture(true,true,21)))
    .file(ai).param("latitude","21.125").param("longitude","79.0875").param("accuracy","5").cookie(cookie))
    .andExpect(status().isCreated()).andExpect(jsonPath("$.attendanceResult").value("PRESENT"))
    .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
    .andExpect(jsonPath("$.livenessStatus").value("VERIFIED"))
    .andExpect(jsonPath("$.evidence.verificationReason").value("EDGE_AI_VERIFIED"))
    .andExpect(jsonPath("$.evidence.latitude").value(21.125))
    .andExpect(jsonPath("$.evidence.locationVerificationStatus").value(student==studentA?"VERIFIED":"WORKPLACE_NOT_APPROVED"))
    .andExpect(jsonPath("$.evidence.exifLatitude").value(org.hamcrest.Matchers.nullValue()));
  }
  long approvedPhoto=submitPhoto(studentA,"StudentPassword!23"),pendingPhoto=submitPhoto(studentB,"StudentPassword!24");
  assertThat(reports.findById(approvedPhoto).orElseThrow().getWorkplaceVerificationStatus()).isNull();
  assertThat(reports.findById(pendingPhoto).orElseThrow().getWorkplaceVerificationStatus()).isNull();
  assertThat(jdbc.queryForObject("select count(*) from attendance_records where verification_status='VERIFIED'",Integer.class)).isEqualTo(2);
 }
 @Test void correctionsRequireAdminScopeAndKeepHistoricalPhotoSnapshot() throws Exception {
  register(studentA,"21.125","79.0875","Original branch");approve(studentA);long id=submitPhoto(studentA,"StudentPassword!23");
  jdbc.update("update monthly_progress_reports set workplace_latitude=21.125,workplace_longitude=79.0875,workplace_verification_status='WITHIN_RADIUS' where id=?",id);
  var correction=new WorkplaceCorrection(new RegisteredWorkplaceRequest("test-corrected","Corrected test address",new BigDecimal("19.076"),new BigDecimal("72.8777"),"Corrected branch"),new BigDecimal("150"),"Incorrect branch selected");
  for(var user:new User[]{mentorA,studentA}) mvc.perform(put("/api/v1/admin/students/"+studentA.getId()+"/internship/location/correction").cookie(login(user,user==mentorA?"MentorPassword!23":"StudentPassword!23")).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(correction))).andExpect(status().isForbidden());
  mvc.perform(put("/api/v1/admin/students/"+studentA.getId()+"/internship/location").cookie(login(admin,"AdminPassword!23")).contentType(MediaType.APPLICATION_JSON).content("{\"latitude\":19.076,\"longitude\":72.8777,\"geofenceRadiusMeters\":150}")).andExpect(status().isConflict());
  mvc.perform(put("/api/v1/admin/students/"+studentA.getId()+"/internship/location/correction").cookie(login(admin,"AdminPassword!23")).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(correction))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
  var original=reports.findById(id).orElseThrow();assertThat(original.getWorkplaceLatitude()).isEqualByComparingTo("21.125");assertThat(original.getWorkplaceVerificationStatus()).isEqualTo("WITHIN_RADIUS");
  assertThat(jdbc.queryForObject("select count(*) from workplace_correction_audit",Integer.class)).isEqualTo(1);
 }
 @Test void missingGpsMissingWorkplaceAndLegacyReferencesRemainExplicit() throws Exception {
  long id=submitPhoto(studentA,"StudentPassword!23");assertThat(reports.findById(id).orElseThrow().getWorkplaceVerificationStatus()).isNull();
  var missing=new PhysicalEvidenceInput(null,null,null,new ValidatedCameraImage("image/jpeg",100,null,null,null));
  assertThat(policy.evaluate(InternshipMode.OFFICE_REPORTING,null,missing,NOW).status()).isEqualTo(VerificationStatus.UNVERIFIED);
  var internship=internships.findByOnboardingStudentId(studentA.getId()).orElseThrow();var legacy=locations.saveAndFlush(new InternshipLocation(internship,new BigDecimal("21.125"),new BigDecimal("79.0875"),new BigDecimal("200"),NOW));
  assertThat(legacy.getPlaceId()).isNull();assertThat(locationService.getForAdmin(studentA.getId()).workplaceAddress()).isNull();
 }
 @Test void pendingPlaceCannotBeOverwrittenByLegacyCoordinateRouteAndPhotoGpsStaysIndependent() throws Exception {
  register(studentA,"21.125","79.0875","Branch A");
  mvc.perform(put("/api/v1/student/internship/location").cookie(login(studentA,"StudentPassword!23")).contentType(MediaType.APPLICATION_JSON).content("{\"latitude\":19,\"longitude\":72,\"geofenceRadiusMeters\":200}")).andExpect(status().isConflict());
  long id=submitPhoto(studentA,"StudentPassword!23"); assertThat(reports.findById(id).orElseThrow().getWorkplaceVerificationStatus()).isNull();
  var missingGps=mvc.perform(multipart("/api/v1/student/monthly-progress/2").file(new MockMultipartFile("photo","without-gps.jpg","image/jpeg",ProgressPhotoValidatorTest.fixture(false,true,21))).file(new MockMultipartFile("workDescription","","text/plain","Missing photo GPS test".getBytes())).cookie(login(studentA,"StudentPassword!23"))).andExpect(status().isCreated()).andReturn();
  long mid=json.readTree(missingGps.getResponse().getContentAsString()).get("id").asLong();assertThat(reports.findById(mid).orElseThrow().getWorkplaceVerificationStatus()).isNull();
 }
 @Test void attendanceSnapshotIsNotRecomputedByWorkplaceCorrection() {
  register(studentA,"21.125","79.0875","Original branch");var internship=internships.findByOnboardingStudentId(studentA.getId()).orElseThrow();
  jdbc.update("insert into attendance_records(student_user_id,internship_id,attendance_date,attendance_result,verification_status,submission_type,server_submitted_at,created_at,updated_at) values (?,?,'2026-11-10','PRESENT','VERIFIED','BASIC',?,?,?)",studentA.getId(),internship.getId(),java.sql.Timestamp.from(NOW),java.sql.Timestamp.from(NOW),java.sql.Timestamp.from(NOW));
  long aid=jdbc.queryForObject("select id from attendance_records where student_user_id=?",Long.class,studentA.getId());
  jdbc.update("insert into attendance_evidence(attendance_id,expected_latitude,expected_longitude,distance_meters,geofence_radius_meters,camera_capture_used,verification_reason,image_media_type,image_size_bytes,created_at) values (?,21.125,79.0875,0,200,true,'VERIFIED_GEOFENCE','image/jpeg',100,?)",aid,java.sql.Timestamp.from(NOW));
  locationService.correctForAdmin(studentA.getId(),UserPrincipal.from(admin),new WorkplaceCorrection(new RegisteredWorkplaceRequest("test-new-branch","New test branch",new BigDecimal("19"),new BigDecimal("72")),new BigDecimal("100"),"Explicit branch correction"));
  assertThat(jdbc.queryForObject("select expected_latitude from attendance_evidence where attendance_id=?",BigDecimal.class,aid)).isEqualByComparingTo("21.125");
  assertThat(jdbc.queryForObject("select verification_status from attendance_records where id=?",String.class,aid)).isEqualTo("VERIFIED");
 }
 @Test void assignedMentorOnlyCanReadWorkplaceAndRadiusChangesRequireReconfirmation() throws Exception {
  register(studentA,"21.125","79.0875","Branch A");approve(studentA);
  mvc.perform(get("/api/v1/mentor/students/"+studentA.getId()+"/internship/location").cookie(login(mentorB,"MentorPassword!24"))).andExpect(status().isNotFound());
  mvc.perform(put("/api/v1/admin/students/"+studentA.getId()+"/internship/location/radius").cookie(login(admin,"AdminPassword!23")).contentType(MediaType.APPLICATION_JSON).content("{\"radiusMeters\":300}")).andExpect(status().isOk()).andExpect(jsonPath("$.geofenceRadiusMeters").value(300)).andExpect(jsonPath("$.workplaceName").value("Branch A")).andExpect(jsonPath("$.status").value("PENDING"));
 }
}
