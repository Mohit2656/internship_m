package edu.institution.ims.monthlyprogress;
import edu.institution.ims.attendance.*;
import edu.institution.ims.internship.InternshipDetailsService;
import edu.institution.ims.internship.dto.*;
import edu.institution.ims.security.UserPrincipal;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DualWorkplaceApprovalIntegrationTest extends SharedWorkplaceIntegrationTest {
 @Autowired InternshipDetailsService internshipDetailsService;
 @Test void assignedMentorApprovalCompletesVerificationWithoutIiicDecisionAndNotifiesOnce() {
  register(studentA,"21.125","79.0875","Branch A");var pending=locationService.getForAdmin(studentA.getId());
  assertThat(pending.status()).isEqualTo(InternshipLocationStatus.PENDING_MENTOR_APPROVAL);
  var decision=new WorkplaceReview(pending.revision(),WorkplaceReview.Decision.APPROVE,"Verified actual office pin");
  var approved=locationService.review(studentA.getId(),UserPrincipal.from(mentorA),decision);
  assertThat(approved.status()).isEqualTo(InternshipLocationStatus.APPROVED);assertThat(approved.iiicDecision()).isNull();assertThat(approved.coordinatesLocked()).isTrue();
  assertThat(locationService.fullyApproved(locations.findById(approved.id()).orElseThrow())).isTrue();
  locationService.review(studentA.getId(),UserPrincipal.from(mentorA),decision);
  assertThat(jdbc.queryForObject("select count(*) from workplace_review_audit where location_id=?",Integer.class,approved.id())).isEqualTo(1);
  assertThat(jdbc.queryForObject("select message from student_notifications where student_user_id=?",String.class,studentA.getId())).contains("verified for attendance").doesNotContain("IIIC");
 }
 @Test void iiicCannotReviewButRetainsReadAndAuditedCorrectionAccess() throws Exception {
  register(studentA,"21.125","79.0875","Branch A");var pending=locationService.getForAdmin(studentA.getId());var cookie=login(admin,"AdminPassword!23");
  mvc.perform(post("/api/v1/admin/students/"+studentA.getId()+"/internship/location/review").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":"+pending.revision()+",\"decision\":\"APPROVE\"}")).andExpect(status().isNotFound());
  assertThatThrownBy(()->locationService.review(studentA.getId(),UserPrincipal.from(admin),new WorkplaceReview(pending.revision(),WorkplaceReview.Decision.APPROVE,null))).isInstanceOfSatisfying(AttendanceException.class,e->assertThat(e.getCode()).isEqualTo("MENTOR_REVIEW_REQUIRED"));
  mvc.perform(get("/api/v1/admin/students/"+studentA.getId()+"/internship/location").cookie(cookie)).andExpect(status().isOk());
  var request=new WorkplaceCorrection(new RegisteredWorkplaceRequest(null,"Corrected address",new BigDecimal("21.126"),new BigDecimal("79.088"),"Corrected branch"),new BigDecimal("200"),"Administrative coordinate correction");
  mvc.perform(put("/api/v1/admin/students/"+studentA.getId()+"/internship/location/correction").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_MENTOR_APPROVAL"));
  assertThat(jdbc.queryForObject("select count(*) from workplace_correction_audit",Integer.class)).isEqualTo(1);
 }
 @Test void unassignedMentorAndStudentCannotApprove() throws Exception {
  register(studentA,"21.125","79.0875","Branch A");var pending=locationService.getForAdmin(studentA.getId());String body="{\"revision\":"+pending.revision()+",\"decision\":\"APPROVE\"}";
  mvc.perform(post("/api/v1/mentor/students/"+studentA.getId()+"/internship/location/review").cookie(login(mentorB,"MentorPassword!24")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
  mvc.perform(post("/api/v1/mentor/students/"+studentA.getId()+"/internship/location/review").cookie(login(studentA,"StudentPassword!23")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
 }
 @Test void rejectionAllowsCorrectionAndFreshApproval() {
  register(studentA,"21.125","79.0875","Wrong branch");var first=locationService.getForAdmin(studentA.getId());
  locationService.review(studentA.getId(),UserPrincipal.from(mentorA),new WorkplaceReview(first.revision(),WorkplaceReview.Decision.REJECT,"Wrong company branch"));
  register(studentA,"21.126","79.088","Correct branch");var next=locationService.getForAdmin(studentA.getId());
  assertThat(next.status()).isEqualTo(InternshipLocationStatus.PENDING_MENTOR_APPROVAL);assertThat(next.revision()).isGreaterThan(first.revision());assertThat(next.mentorDecision()).isNull();
  assertThat(locationService.review(studentA.getId(),UserPrincipal.from(mentorA),new WorkplaceReview(next.revision(),WorkplaceReview.Decision.APPROVE,null)).status()).isEqualTo(InternshipLocationStatus.APPROVED);
 }
 @Test void adminCorrectionInvalidatesApprovalAndPreservesAudit() {
  register(studentA,"21.125","79.0875","Original branch");approve(studentA);var approved=locationService.getForAdmin(studentA.getId());
  var corrected=locationService.correctForAdmin(studentA.getId(),UserPrincipal.from(admin),new WorkplaceCorrection(new RegisteredWorkplaceRequest(null,"Corrected address",new BigDecimal("19.076"),new BigDecimal("72.8777"),"Corrected branch"),new BigDecimal("200"),"Wrong office originally selected"));
  assertThat(corrected.status()).isEqualTo(InternshipLocationStatus.PENDING_MENTOR_APPROVAL);assertThat(corrected.mentorDecision()).isNull();assertThat(corrected.revision()).isGreaterThan(approved.revision());
  assertThat(jdbc.queryForObject("select count(*) from workplace_review_audit where location_id=?",Integer.class,approved.id())).isEqualTo(1);assertThat(jdbc.queryForObject("select count(*) from workplace_correction_audit",Integer.class)).isEqualTo(1);
 }
 @Test void historicalIiicDecisionIsRetainedButDoesNotControlEligibility() {
  register(studentA,"21.125","79.0875","Legacy branch");var location=locationService.getForAdmin(studentA.getId());
  jdbc.update("update internship_locations set iiic_decision='REJECT',iiic_remarks='Historical decision retained',confirmed_by=?,confirmed_at=? where id=?",admin.getId(),java.sql.Timestamp.from(NOW),location.id());
  var approved=locationService.review(studentA.getId(),UserPrincipal.from(mentorA),new WorkplaceReview(location.revision(),WorkplaceReview.Decision.APPROVE,null));
  assertThat(approved.status()).isEqualTo(InternshipLocationStatus.APPROVED);assertThat(approved.iiicDecision()).isEqualTo("REJECT");assertThat(approved.iiicRemarks()).isEqualTo("Historical decision retained");
 }
 @Test void studentCoordinatesUpdateInvalidatesPreviousApprovalAndIdempotentUpdatesRemainApproved() throws Exception {
  register(studentA,"21.125","79.0875","Branch A");var original=locationService.getForAdmin(studentA.getId());locationService.review(studentA.getId(),UserPrincipal.from(mentorA),new WorkplaceReview(original.revision(),WorkplaceReview.Decision.APPROVE,null));
  var approved=locationService.getForAdmin(studentA.getId());var cookie=login(studentA,"StudentPassword!23");
  mvc.perform(put("/api/v1/student/internship").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update(new RegisteredWorkplaceRequest(null,approved.workplaceAddress(),approved.latitude(),approved.longitude(),approved.workplaceName()),"Renamed Company")))).andExpect(status().isOk()).andExpect(jsonPath("$.companyName").value("Renamed Company"));
  assertThat(locationService.getForAdmin(studentA.getId()).status()).isEqualTo(InternshipLocationStatus.APPROVED);
  mvc.perform(put("/api/v1/student/internship").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update(new RegisteredWorkplaceRequest(null,approved.workplaceAddress(),new BigDecimal("21.126"),approved.longitude(),approved.workplaceName()),"Company")))).andExpect(status().isOk());
  assertThat(locationService.getForAdmin(studentA.getId()).status()).isEqualTo(InternshipLocationStatus.PENDING_MENTOR_APPROVAL);
 }
 @Test void concurrentApprovalAndStudentUpdateCannotCommitApprovedChangedPair() throws Exception {
  register(studentA,"21.125","79.0875","Concurrent branch");var original=locationService.getForAdmin(studentA.getId());var change=update(new RegisteredWorkplaceRequest(null,"Changed branch",new BigDecimal("19.076"),new BigDecimal("72.8777"),"Changed branch"),"Company");
  var start=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
  try{var a=pool.submit(()->{start.await();try{locationService.review(studentA.getId(),UserPrincipal.from(mentorA),new WorkplaceReview(original.revision(),WorkplaceReview.Decision.APPROVE,null));}catch(Throwable ignored){}return null;});var b=pool.submit(()->{start.await();try{internshipDetailsService.updateOwn(UserPrincipal.from(studentA),change);}catch(Throwable ignored){}return null;});start.countDown();a.get(15,java.util.concurrent.TimeUnit.SECONDS);b.get(15,java.util.concurrent.TimeUnit.SECONDS);}finally{pool.shutdownNow();}
  var committed=locationService.getForAdmin(studentA.getId());assertThat("APPROVE".equals(committed.mentorDecision())&&committed.latitude().compareTo(new BigDecimal("19.076"))==0).isFalse();
 }
 private InternshipDetailsRequest update(RegisteredWorkplaceRequest workplace,String name){var c=internships.findByOnboardingStudentId(studentA.getId()).orElseThrow();return new InternshipDetailsRequest(name,c.getCompanyType(),c.getOtherCompanyType(),"Updated mailing address",c.getCompanyPhone(),c.getIndustrySupervisorName(),c.getIndustrySupervisorDesignation(),c.getIndustrySupervisorContact(),c.getDurationMonths(),c.getStartDate(),c.getEndDate(),c.getTotalWeeks(),c.getInternshipMode(),c.getStipendPerMonth(),c.isFullTimeEmploymentOffered(),workplace);}
}
