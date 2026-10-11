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
 @Test void savingUnchangedRadiusPreservesApprovalsButARealChangeRevokesThem() throws Exception {
  register(studentA,"21.125","79.0875","Branch A");approve(studentA);
  long reportId=submitPhoto(studentA,"StudentPassword!23");
  // Seed a retained legacy snapshot; current uploads never perform workplace verification.
  jdbc.update("update monthly_progress_reports set workplace_radius_meters=200,workplace_verification_status='WITHIN_RADIUS' where id=?",reportId);
  var before=locationService.getForAdmin(studentA.getId());
  var same=locationService.radiusForAdmin(studentA.getId(),UserPrincipal.from(admin),new BigDecimal("200.00"));
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
  assertThat(jdbc.queryForObject("select count(*) from workplace_review_audit where radius_meters=200",Integer.class)).isEqualTo(2);
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
