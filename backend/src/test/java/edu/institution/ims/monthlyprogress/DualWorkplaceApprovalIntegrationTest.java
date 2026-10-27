package edu.institution.ims.monthlyprogress;
import edu.institution.ims.attendance.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.internship.dto.RegisteredWorkplaceRequest;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class DualWorkplaceApprovalIntegrationTest extends SharedWorkplaceIntegrationTest {
 @Test void studentReceivesSeparateDurableApprovalNoticesWithoutDuplicatesAndNewRevisionNotifiesAgain() throws Exception {
  register(studentA,"21.125","79.0875","Branch A");
  var original=locationService.getForAdmin(studentA.getId());
  var decision=new WorkplaceReview(original.revision(),WorkplaceReview.Decision.APPROVE,null);
  var mentorPrincipal=UserPrincipal.from(mentorA);var adminPrincipal=UserPrincipal.from(admin);
  var studentCookie=login(studentA,"StudentPassword!23");
  locationService.review(studentA.getId(),mentorPrincipal,decision);
  mvc.perform(get("/api/v1/student/notifications").cookie(studentCookie)).andExpect(status().isOk())
   .andExpect(jsonPath("$.unreadCount").value(1)).andExpect(jsonPath("$.content[0].type").value("INTERNSHIP_MENTOR_APPROVED"))
   .andExpect(jsonPath("$.content[0].title").value("Internship approved by faculty mentor"))
   .andExpect(jsonPath("$.content[0].message").value(org.hamcrest.Matchers.containsString("IIIC coordinator is still required")))
   .andExpect(jsonPath("$.content[0].actionUrl").value("/student/internship/details"));
  assertThat(locationService.getOwn(UserPrincipal.from(studentA)).status()).isEqualTo(InternshipLocationStatus.PENDING);
  locationService.review(studentA.getId(),mentorPrincipal,decision);
  locationService.review(studentA.getId(),adminPrincipal,decision);
  locationService.review(studentA.getId(),adminPrincipal,decision);
  var notices=jdbc.queryForList("select notification_type from student_notifications where student_user_id=?",String.class,studentA.getId());
  assertThat(notices).containsExactlyInAnyOrder("INTERNSHIP_MENTOR_APPROVED","INTERNSHIP_IIIC_APPROVED");
  mvc.perform(get("/api/v1/student/notifications").cookie(studentCookie)).andExpect(status().isOk())
   .andExpect(jsonPath("$.unreadCount").value(2)).andExpect(jsonPath("$.content[0].type").value("INTERNSHIP_IIIC_APPROVED"))
   .andExpect(jsonPath("$.content[0].title").value("Internship approved by IIIC coordinator"))
   .andExpect(jsonPath("$.content[0].message").value(org.hamcrest.Matchers.containsString("Both mentor and IIIC coordinator approvals are complete")));
  long mentorNotice=jdbc.queryForObject("select id from student_notifications where student_user_id=? and notification_type='INTERNSHIP_MENTOR_APPROVED'",Long.class,studentA.getId());
  mvc.perform(patch("/api/v1/student/notifications/"+mentorNotice+"/read").cookie(studentCookie)).andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(1));
  mvc.perform(get("/api/v1/student/notifications").cookie(login(studentB,"StudentPassword!24"))).andExpect(status().isOk())
   .andExpect(jsonPath("$.totalElements").value(0));
  var next=locationService.radiusForAdmin(studentA.getId(),adminPrincipal,new BigDecimal("250"));
  assertThat(next.revision()).isGreaterThan(original.revision());
  assertThatThrownBy(() -> locationService.review(studentA.getId(),mentorPrincipal,decision)).isInstanceOf(AttendanceException.class);
  locationService.review(studentA.getId(),adminPrincipal,new WorkplaceReview(next.revision(),WorkplaceReview.Decision.APPROVE,null));
  locationService.review(studentA.getId(),mentorPrincipal,new WorkplaceReview(next.revision(),WorkplaceReview.Decision.APPROVE,null));
  assertThat(jdbc.queryForObject("select count(*) from student_notifications where student_user_id=?",Integer.class,studentA.getId())).isEqualTo(4);
  assertThat(jdbc.queryForObject("select count(*) from student_notifications where student_user_id=? and read_at is not null",Integer.class,studentA.getId())).isEqualTo(1);
 }
 @Test void reviewEndpointsCommitBothApprovalOrdersAndStudentReloadsTheCorrectWorkplace() throws Exception {
  register(studentA,"21.125","79.0875","Branch A");
  register(studentB,"19.076","72.8777","Branch B");
  var adminCookie=login(admin,"AdminPassword!23");
  for(var student:new edu.institution.ims.user.User[]{studentA,studentB}) {
   boolean mentorFirst=student.getId().equals(studentA.getId());
   var mentor=mentorFirst?mentorA:mentorB;
   var mentorCookie=login(mentor,mentorFirst?"MentorPassword!23":"MentorPassword!24");
   var studentCookie=login(student,mentorFirst?"StudentPassword!23":"StudentPassword!24");
   var original=locationService.getForAdmin(student.getId());
   String mentorUrl="/api/v1/mentor/students/"+student.getId()+"/internship/location/review";
   String adminUrl="/api/v1/admin/students/"+student.getId()+"/internship/location/review";
   String body=json.writeValueAsString(new WorkplaceReview(original.revision(),WorkplaceReview.Decision.APPROVE,"Reviewed actual branch"));
   mvc.perform(post(mentorFirst?mentorUrl:adminUrl).cookie(mentorFirst?mentorCookie:adminCookie)
    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
    .andExpect(jsonPath("$.id").value(original.id()))
    .andExpect(jsonPath("$.internshipId").value(original.internshipId()))
    .andExpect(jsonPath("$.status").value("PENDING"))
    .andExpect(jsonPath(mentorFirst?"$.mentorDecision":"$.iiicDecision").value("APPROVE"));
   // JDBC and the following HTTP requests use new transactions after review has committed.
   assertThat(jdbc.queryForObject("select status from internship_locations where id=?",String.class,original.id())).isEqualTo("PENDING");
   mvc.perform(get("/api/v1/student/internship/location").cookie(studentCookie)).andExpect(status().isOk())
    .andExpect(jsonPath("$.id").value(original.id())).andExpect(jsonPath("$.status").value("PENDING"));
   mvc.perform(post(mentorFirst?adminUrl:mentorUrl).cookie(mentorFirst?adminCookie:mentorCookie)
    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
    .andExpect(jsonPath("$.id").value(original.id())).andExpect(jsonPath("$.status").value("APPROVED"))
    .andExpect(jsonPath("$.mentorDecision").value("APPROVE")).andExpect(jsonPath("$.iiicDecision").value("APPROVE"))
    .andExpect(jsonPath("$.mentorReviewerId").value(mentor.getId())).andExpect(jsonPath("$.confirmedBy").value(admin.getId()))
    .andExpect(jsonPath("$.mentorReviewedAt").value(NOW.toString())).andExpect(jsonPath("$.confirmedAt").value(NOW.toString()));
   assertThat(jdbc.queryForObject("select status from internship_locations where id=?",String.class,original.id())).isEqualTo("APPROVED");
   assertThat(jdbc.queryForObject("select mentor_decision from internship_locations where id=?",String.class,original.id())).isEqualTo("APPROVE");
   assertThat(jdbc.queryForObject("select iiic_decision from internship_locations where id=?",String.class,original.id())).isEqualTo("APPROVE");
   assertThat(jdbc.queryForObject("select mentor_approved_by from internship_locations where id=?",Long.class,original.id())).isEqualTo(mentor.getId());
   assertThat(jdbc.queryForObject("select confirmed_by from internship_locations where id=?",Long.class,original.id())).isEqualTo(admin.getId());
   assertThat(jdbc.queryForObject("select mentor_reviewed_at from internship_locations where id=?",java.sql.Timestamp.class,original.id()).toInstant()).isEqualTo(NOW);
   assertThat(jdbc.queryForObject("select confirmed_at from internship_locations where id=?",java.sql.Timestamp.class,original.id()).toInstant()).isEqualTo(NOW);
   assertThat(jdbc.queryForObject("select count(*) from internship_locations where internship_id=?",Integer.class,original.internshipId())).isEqualTo(1);
   assertThat(jdbc.queryForObject("select count(*) from workplace_review_audit where location_id=? and revision=? and decision='APPROVE' and latitude=? and longitude=?",Integer.class,original.id(),original.revision(),original.latitude(),original.longitude())).isEqualTo(2);
   for(int reload=0;reload<2;reload++) mvc.perform(get("/api/v1/student/internship/location").cookie(studentCookie))
    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(original.id()))
    .andExpect(jsonPath("$.status").value("APPROVED")).andExpect(jsonPath("$.revision").value(original.revision()))
    .andExpect(jsonPath("$.latitude").value(original.latitude().doubleValue())).andExpect(jsonPath("$.longitude").value(original.longitude().doubleValue()))
    .andExpect(jsonPath("$.workplaceName").value(original.workplaceName())).andExpect(jsonPath("$.workplaceAddress").value(original.workplaceAddress()));
   mvc.perform(get(mentorUrl.replace("/review","")).cookie(mentorCookie)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
   mvc.perform(get(adminUrl.replace("/review","")).cookie(adminCookie)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
   if(mentorFirst) assertThat(locationService.getOwn(UserPrincipal.from(studentB)).status()).isEqualTo(InternshipLocationStatus.PENDING);
  }
 }
 @Test void rejectedDecisionIsCommittedAndReturnedToTheStudentAfterReload() throws Exception {
  register(studentA,"21.125","79.0875","Branch A");
  var original=locationService.getForAdmin(studentA.getId());
  locationService.review(studentA.getId(),UserPrincipal.from(mentorA),new WorkplaceReview(original.revision(),WorkplaceReview.Decision.APPROVE,null));
  String reason="Selected the wrong company branch";
  mvc.perform(post("/api/v1/admin/students/"+studentA.getId()+"/internship/location/review")
   .cookie(login(admin,"AdminPassword!23")).contentType(MediaType.APPLICATION_JSON)
   .content(json.writeValueAsString(new WorkplaceReview(original.revision(),WorkplaceReview.Decision.REJECT,reason))))
   .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
  assertThat(jdbc.queryForObject("select status from internship_locations where id=?",String.class,original.id())).isEqualTo("REJECTED");
  assertThat(jdbc.queryForObject("select iiic_remarks from internship_locations where id=?",String.class,original.id())).isEqualTo(reason);
  mvc.perform(get("/api/v1/student/internship/location").cookie(login(studentA,"StudentPassword!23")))
   .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(original.id())).andExpect(jsonPath("$.status").value("REJECTED"))
   .andExpect(jsonPath("$.mentorDecision").value("APPROVE")).andExpect(jsonPath("$.iiicDecision").value("REJECT"))
   .andExpect(jsonPath("$.iiicRemarks").value(reason)).andExpect(jsonPath("$.confirmedBy").value(admin.getId()))
   .andExpect(jsonPath("$.latitude").value(21.125)).andExpect(jsonPath("$.longitude").value(79.0875));
  assertThat(jdbc.queryForObject("select count(*) from workplace_review_audit where location_id=?",Integer.class,original.id())).isEqualTo(2);
 }
 @Test void failedLocationUpdateDoesNotReportApprovalOrChangeTheCommittedRecord() throws Exception {
  assertFailedApprovalRollsBack("internship_locations","ck_test_workplace_update_failure","status<>'APPROVED'");
 }
 @Test void failedAuditInsertRollsBackTheAlreadyFlushedApproval() throws Exception {
  assertFailedApprovalRollsBack("workplace_review_audit","ck_test_workplace_audit_failure","reviewer_role<>'ADMIN'");
 }
 @Test void failedStudentNotificationInsertRollsBackApprovalAndAudit() throws Exception {
  assertFailedApprovalRollsBack("student_notifications","ck_test_workplace_notification_failure","notification_type<>'INTERNSHIP_IIIC_APPROVED'");
 }
 private void assertFailedApprovalRollsBack(String table,String constraint,String check) throws Exception {
  // These failure-injection constraints are permitted only in the in-memory test database
  // or the disposable PostgreSQL schema required by the inherited Postgres test class.
  try(var connection=java.util.Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
   String url=connection.getMetaData().getURL();
   if(!url.startsWith("jdbc:h2:mem:ims")) {
    String schema=System.getenv("EXIF_TEST_DB_SCHEMA");
    assertThat(schema).matches("ims_deletion_test_[0-9a-f]{32}");
    assertThat(url).startsWith("jdbc:postgresql://").contains("currentSchema="+schema);
   }
  }
  register(studentA,"21.125","79.0875","Branch A");
  var original=locationService.getForAdmin(studentA.getId());
  locationService.review(studentA.getId(),UserPrincipal.from(mentorA),new WorkplaceReview(original.revision(),WorkplaceReview.Decision.APPROVE,null));
  var adminCookie=login(admin,"AdminPassword!23");
  String url="/api/v1/admin/students/"+studentA.getId()+"/internship/location/review";
  String body=json.writeValueAsString(new WorkplaceReview(original.revision(),WorkplaceReview.Decision.APPROVE,"IIIC reviewed branch"));
  jdbc.execute("alter table "+table+" add constraint "+constraint+" check ("+check+")");
  try {
   mvc.perform(post(url).cookie(adminCookie).contentType(MediaType.APPLICATION_JSON).content(body))
    .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(400,599));
   assertThat(jdbc.queryForObject("select status from internship_locations where id=?",String.class,original.id())).isEqualTo("PENDING");
   assertThat(jdbc.queryForObject("select mentor_decision from internship_locations where id=?",String.class,original.id())).isEqualTo("APPROVE");
   assertThat(jdbc.queryForObject("select iiic_decision from internship_locations where id=?",String.class,original.id())).isNull();
   assertThat(jdbc.queryForObject("select confirmed_by from internship_locations where id=?",Long.class,original.id())).isNull();
   assertThat(jdbc.queryForObject("select confirmed_at from internship_locations where id=?",java.sql.Timestamp.class,original.id())).isNull();
   assertThat(jdbc.queryForObject("select count(*) from workplace_review_audit where location_id=?",Integer.class,original.id())).isEqualTo(1);
   assertThat(jdbc.queryForObject("select count(*) from student_notifications where student_user_id=? and notification_type='INTERNSHIP_MENTOR_APPROVED'",Integer.class,studentA.getId())).isEqualTo(1);
   assertThat(jdbc.queryForObject("select count(*) from student_notifications where student_user_id=? and notification_type='INTERNSHIP_IIIC_APPROVED'",Integer.class,studentA.getId())).isZero();
   mvc.perform(get("/api/v1/student/internship/location").cookie(login(studentA,"StudentPassword!23")))
    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
    .andExpect(jsonPath("$.mentorDecision").value("APPROVE")).andExpect(jsonPath("$.iiicDecision").isEmpty());
  } finally {
   jdbc.execute("alter table "+table+" drop constraint "+constraint);
  }
  mvc.perform(post(url).cookie(adminCookie).contentType(MediaType.APPLICATION_JSON).content(body))
   .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
  assertThat(jdbc.queryForObject("select status from internship_locations where id=?",String.class,original.id())).isEqualTo("APPROVED");
  assertThat(jdbc.queryForObject("select count(*) from workplace_review_audit where location_id=?",Integer.class,original.id())).isEqualTo(2);
  assertThat(jdbc.queryForObject("select count(*) from student_notifications where student_user_id=? and notification_type='INTERNSHIP_IIIC_APPROVED'",Integer.class,studentA.getId())).isEqualTo(1);
 }
 @Test void savingUnchangedRadiusPreservesApprovalsButARealChangeRevokesThem() throws Exception {
  register(studentA,"21.125","79.0875","Branch A");approve(studentA);
  long reportId=submitPhoto(studentA,"StudentPassword!23");
  // Seed a retained legacy snapshot; current uploads never perform workplace verification.
  jdbc.update("update monthly_progress_reports set workplace_radius_meters=200,workplace_verification_status='WITHIN_RADIUS' where id=?",reportId);
  var before=locationService.getForAdmin(studentA.getId());
  var same=locationService.radiusForAdmin(studentA.getId(),UserPrincipal.from(admin),new BigDecimal("100.00"));
  assertThat(same.status()).isEqualTo(InternshipLocationStatus.APPROVED);
  assertThat(same.revision()).isEqualTo(before.revision());
  var changed=locationService.radiusForAdmin(studentA.getId(),UserPrincipal.from(admin),new BigDecimal("250"));
  assertThat(changed.status()).isEqualTo(InternshipLocationStatus.PENDING);
  assertThat(changed.revision()).isEqualTo(before.revision()+1);
  assertThat(changed.mentorDecision()).isNull();assertThat(changed.iiicDecision()).isNull();
  var historical=reports.findById(reportId).orElseThrow();
  assertThat(historical.getWorkplaceRadiusMeters()).isEqualByComparingTo("200");
  assertThat(historical.getWorkplaceVerificationStatus()).isEqualTo("WITHIN_RADIUS");
 }
 @Test void mentorReassignmentInvalidatesEligibilityWithoutChangingHistoricalReport() throws Exception {
  register(studentA,"21.125","79.0875","Branch A");approve(studentA);
  long reportId=submitPhoto(studentA,"StudentPassword!23");
  jdbc.update("update monthly_progress_reports set workplace_radius_meters=200,workplace_verification_status='WITHIN_RADIUS' where id=?",reportId);
  var previous=assignments.findByStudentIdAndActiveTrue(studentA.getId()).orElseThrow();previous.end(NOW);assignments.saveAndFlush(previous);
  assignments.saveAndFlush(new edu.institution.ims.mentor.MentorStudentAssignment(mentorB,studentA,admin,NOW));
  assertThat(locationService.getForAdmin(studentA.getId()).status()).isEqualTo(InternshipLocationStatus.PENDING);
  var newCoordinator=users.saveAndFlush(new edu.institution.ims.user.User("new.coordinator@example.edu","unused",edu.institution.ims.user.Role.ADMIN,true));
  var assignedRevision=locationService.getForAdmin(studentA.getId());
  assertThat(locationService.review(studentA.getId(),UserPrincipal.from(newCoordinator),new WorkplaceReview(assignedRevision.revision(),WorkplaceReview.Decision.APPROVE,null)).status()).isEqualTo(InternshipLocationStatus.PENDING);
  var coordinatorNotice=jdbc.queryForObject("select message from student_notifications where student_user_id=? and event_key=?",String.class,studentA.getId(),"internship-approved:"+assignedRevision.id()+":"+assignedRevision.revision()+":ADMIN:"+newCoordinator.getId());
  assertThat(coordinatorNotice).contains("faculty mentor is still required").doesNotContain("approvals are complete");
  assertThat(reports.findById(reportId).orElseThrow().getWorkplaceVerificationStatus()).isEqualTo("WITHIN_RADIUS");
  mvc.perform(get("/api/v1/mentor/students/"+studentA.getId()+"/internship/location").cookie(login(mentorA,"MentorPassword!23"))).andExpect(status().isNotFound());
  mvc.perform(post("/api/v1/mentor/monthly-progress/"+reportId+"/approve").cookie(login(mentorA,"MentorPassword!23")).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}")).andExpect(status().isNotFound());
  mvc.perform(post("/api/v1/mentor/monthly-progress/"+reportId+"/approve").cookie(login(mentorB,"MentorPassword!24")).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
  var current=locationService.getForAdmin(studentA.getId());
  locationService.review(studentA.getId(),UserPrincipal.from(mentorB),new WorkplaceReview(current.revision(),WorkplaceReview.Decision.APPROVE,"New assigned mentor reviewed"));
  assertThat(locationService.getForAdmin(studentA.getId()).status()).isEqualTo(InternshipLocationStatus.APPROVED);
 }
 @Test void reviewAuditRetainsReferenceAfterCorrectionAndInactiveReviewerCannotVerify() {
  register(studentA,"21.125","79.0875","Branch A");approve(studentA);
  jdbc.update("update users set active=false where id=?",mentorA.getId());
  assertThat(locationService.getForAdmin(studentA.getId()).status()).isEqualTo(InternshipLocationStatus.PENDING);
  register(studentA,"19","72","Changed branch");
  assertThat(jdbc.queryForObject("select min(latitude) from workplace_review_audit",BigDecimal.class)).isEqualByComparingTo("21.125");
  assertThat(jdbc.queryForObject("select count(*) from workplace_review_audit where radius_meters=100",Integer.class)).isEqualTo(2);
 }
 @Test void oneApprovalIsInsufficientAndChangedCoordinatesInvalidateBoth() throws Exception {
  register(studentA,"21.125","79.0875","Branch A");var p=UserPrincipal.from(admin);
  var first=locationService.review(studentA.getId(),p,new WorkplaceReview(locationService.getForAdmin(studentA.getId()).revision(),WorkplaceReview.Decision.APPROVE,null));assertThat(first.status()).isEqualTo(InternshipLocationStatus.PENDING);assertThat(first.mentorDecision()).isNull();
  var loc=locations.findByInternshipId(internships.findByOnboardingStudentId(studentA.getId()).orElseThrow().getId()).orElseThrow();assertThat(locationService.fullyApproved(loc)).isFalse();
  locationService.review(studentA.getId(),UserPrincipal.from(mentorA),new WorkplaceReview(first.revision(),WorkplaceReview.Decision.APPROVE,"Actual branch reviewed"));
  loc=locations.findByInternshipId(loc.getInternship().getId()).orElseThrow();assertThat(locationService.fullyApproved(loc)).isTrue();
  register(studentA,"19","72","Changed branch");var changed=locationService.getForAdmin(studentA.getId());assertThat(changed.revision()).isGreaterThan(first.revision());assertThat(changed.mentorDecision()).isNull();assertThat(changed.iiicDecision()).isNull();assertThat(changed.status()).isEqualTo(InternshipLocationStatus.PENDING);
  mvc.perform(post("/api/v1/mentor/students/"+studentA.getId()+"/internship/location/review").cookie(login(mentorA,"MentorPassword!23")).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":"+first.revision()+",\"decision\":\"APPROVE\"}")).andExpect(status().isConflict());
 }
 @Test void rejectionRequiresReasonAndSamePinResubmissionStartsNewRevision() throws Exception {
  register(studentA,"21.125","79.0875","Branch A");var first=locationService.getForAdmin(studentA.getId());
  mvc.perform(post("/api/v1/mentor/students/"+studentA.getId()+"/internship/location/review").cookie(login(mentorA,"MentorPassword!23")).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":"+first.revision()+",\"decision\":\"REJECT\"}")).andExpect(status().isBadRequest());
  locationService.review(studentA.getId(),UserPrincipal.from(mentorA),new WorkplaceReview(first.revision(),WorkplaceReview.Decision.REJECT,"Wrong company branch"));assertThat(locationService.getForAdmin(studentA.getId()).status()).isEqualTo(InternshipLocationStatus.REJECTED);
  register(studentA,"21.125","79.0875","Branch A");var next=locationService.getForAdmin(studentA.getId());assertThat(next.status()).isEqualTo(InternshipLocationStatus.PENDING);assertThat(next.revision()).isGreaterThan(first.revision());
  assertThat(jdbc.queryForObject("select count(*) from workplace_review_audit",Integer.class)).isEqualTo(1);
 }
 @Test void unassignedMentorAndStudentCannotApproveAndLegacyConfirmationIsNotApproval() throws Exception {
  register(studentA,"21.125","79.0875","Branch A");var first=locationService.getForAdmin(studentA.getId());String body="{\"revision\":"+first.revision()+",\"decision\":\"APPROVE\"}";
  mvc.perform(post("/api/v1/mentor/students/"+studentA.getId()+"/internship/location/review").cookie(login(mentorB,"MentorPassword!24")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
  mvc.perform(post("/api/v1/mentor/students/"+studentA.getId()+"/internship/location/review").cookie(login(studentA,"StudentPassword!23")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
  var i=internships.findByOnboardingStudentId(studentB.getId()).orElseThrow();var legacy=new InternshipLocation(i,new BigDecimal("19"),new BigDecimal("72"),new BigDecimal("200"),NOW);legacy.confirm(admin,NOW);locations.saveAndFlush(legacy);assertThat(locationService.fullyApproved(legacy)).isFalse();
 }
 @Test void pinWithoutPlaceIdIsPersistedAndEligibleForSeparateApprovals() {
  var i=internships.findByOnboardingStudentId(studentA.getId()).orElseThrow();locationService.registerSelectedPlace(i,new RegisteredWorkplaceRequest(null,"Actual pin address",new BigDecimal("21.125"),new BigDecimal("79.0875"),"Pin branch"));approve(studentA);
  var l=locations.findByInternshipId(i.getId()).orElseThrow();assertThat(l.getPlaceId()).isNull();assertThat(locationService.fullyApproved(l)).isTrue();
 }
}
