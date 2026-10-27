package edu.institution.ims.attendance;

import edu.institution.ims.internship.InternshipDetails;
import edu.institution.ims.internship.InternshipDetailsRepository;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;

@Service
public class InternshipLocationService {
    private final InternshipLocationRepository locations;
    private final InternshipDetailsRepository internships;
    private final UserRepository users;
    private final Clock clock;
    private final BigDecimal defaultRadius;
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired private edu.institution.ims.mentor.MentorStudentAssignmentRepository assignments;
    @org.springframework.beans.factory.annotation.Autowired private edu.institution.ims.notification.NotificationService notifications;

    public InternshipLocationService(InternshipLocationRepository locations,
            InternshipDetailsRepository internships, UserRepository users, Clock clock,
            @Value("${app.attendance.default-geofence-radius-meters:100}") BigDecimal defaultRadius) {
        this.locations = locations;
        this.internships = internships;
        this.users = users;
        this.clock = clock;
        this.defaultRadius = defaultRadius;
    }

    @Transactional(readOnly = true)
    public InternshipLocationResponse getOwn(UserPrincipal principal) {
        InternshipDetails internship = ownInternship(principal.id());
        return response(locations.findByInternshipId(internship.getId()).orElseThrow(InternshipLocationService::notFound));
    }

    @Transactional
    public InternshipLocationResponse proposeOwn(UserPrincipal principal, InternshipLocationRequest request) {
        throw error(HttpStatus.CONFLICT,"MAP_REGISTRATION_REQUIRED","Select the actual workplace on the map in your internship form");
    }

    @Transactional(readOnly = true)
    public InternshipLocationResponse getForAdmin(Long studentId) {
        InternshipDetails internship = ownInternship(studentId);
        return response(locations.findByInternshipId(internship.getId()).orElseThrow(InternshipLocationService::notFound));
    }

    @Transactional
    public InternshipLocationResponse updateForAdmin(Long studentId, InternshipLocationRequest request) {
        throw error(HttpStatus.CONFLICT,"MAP_REGISTRATION_REQUIRED","Use map-based correction or the radius configuration endpoint");
    }

    @Transactional
    public InternshipLocationResponse confirmForAdmin(Long studentId, UserPrincipal principal) {
        throw error(HttpStatus.CONFLICT,"WORKPLACE_REVIEW_REQUIRED","Review the current workplace revision through the review endpoint");
    }

    @Transactional
    public void registerSelectedPlace(InternshipDetails internship, edu.institution.ims.internship.dto.RegisteredWorkplaceRequest place) {
        if (place == null) return;
        var existing = locations.findByInternshipId(internship.getId());
        if (existing.isPresent()) {
            var current = existing.get();
            boolean same = current.getLatitude().compareTo(place.latitude().setScale(6, java.math.RoundingMode.HALF_UP)) == 0
                && current.getLongitude().compareTo(place.longitude().setScale(6, java.math.RoundingMode.HALF_UP)) == 0
                && java.util.Objects.equals(current.getPlaceId(), place.placeId())
                && java.util.Objects.equals(current.getWorkplaceAddress(), place.address())
                && java.util.Objects.equals(current.getWorkplaceName(), place.name());
            if (same && current.getStatus()!=InternshipLocationStatus.REJECTED) return;

        }
        var location = existing.orElseGet(() -> new InternshipLocation(internship, place.latitude(), place.longitude(), defaultRadius, clock.instant()));
        if (existing.isPresent()) location.updateProposal(place.latitude(), place.longitude(), location.getGeofenceRadiusMeters(), clock.instant());
        location.selectedPlace(place.placeId(), place.address());
        location.name(place.name());
        internship.attachWorkplace(location);
        locations.saveAndFlush(location);
    }

    /** Company edits cannot silently retain approval of a different workplace. */
    @Transactional
    public void invalidateForCompanyChange(InternshipDetails internship, boolean addressChanged,
            boolean selectedPlaceProvided) {
        locations.findByInternshipId(internship.getId()).ifPresent(location -> {
            location.updateProposal(location.getLatitude(), location.getLongitude(),
                    location.getGeofenceRadiusMeters(), clock.instant());
            if (addressChanged && !selectedPlaceProvided) {
                // A changed free-text address needs a fresh confirmed map selection before review.
                location.selectedPlace(null, null);
                location.name(null);
            }
            locations.saveAndFlush(location);
        });
    }

    @Transactional(readOnly=true)
    public InternshipLocationResponse getForMentor(Long studentId, UserPrincipal principal) {
        if (!"MENTOR".equals(principal.role()) || assignments.findByStudentIdAndActiveTrue(studentId).filter(a -> a.getMentor().getId().equals(principal.id())).isEmpty())
            throw error(HttpStatus.NOT_FOUND,"INTERNSHIP_LOCATION_NOT_FOUND","Workplace was not found in your assigned scope");
        return getForAdmin(studentId);
    }

    @Transactional
    public InternshipLocationResponse correctForAdmin(Long studentId, UserPrincipal principal, WorkplaceCorrection request) {
        if (!"ADMIN".equals(principal.role())) throw error(HttpStatus.FORBIDDEN,"ADMIN_REQUIRED","Only the III Cell can correct workplaces");
        if (request.reason().strip().length()<5) throw error(HttpStatus.BAD_REQUEST,"CORRECTION_REASON_REQUIRED","Provide a correction reason of at least five characters");
        var internship=ownInternship(studentId);
        var old=locations.findByInternshipId(internship.getId());
        var oldLatitude=old.map(InternshipLocation::getLatitude).orElse(null);
        var oldLongitude=old.map(InternshipLocation::getLongitude).orElse(null);
        var oldPlace=old.map(InternshipLocation::getPlaceId).orElse(null);
        var place=request.workplace();
        var location=old.orElseGet(() -> new InternshipLocation(internship,place.latitude(),place.longitude(),defaultRadius,clock.instant()));
        location.updateProposal(place.latitude(),place.longitude(),request.radiusMeters(),clock.instant());
        location.selectedPlace(place.placeId(),place.address()); location.name(place.name());
        internship.attachWorkplace(location); locations.saveAndFlush(location);
        jdbc.update("insert into workplace_correction_audit(internship_id,correcting_user_id,corrected_at,reason,old_latitude,old_longitude,old_place_id,new_latitude,new_longitude,new_place_id,new_radius_meters) values (?,?,?,?,?,?,?,?,?,?,?)",
            internship.getId(),principal.id(),java.sql.Timestamp.from(clock.instant()),request.reason().strip(),oldLatitude,oldLongitude,oldPlace,location.getLatitude(),location.getLongitude(),location.getPlaceId(),location.getGeofenceRadiusMeters());
        return response(location);
    }

    @Transactional
    public InternshipLocationResponse radiusForAdmin(Long studentId, UserPrincipal principal, java.math.BigDecimal radius) {
        if (!"ADMIN".equals(principal.role())) throw error(HttpStatus.FORBIDDEN,"ADMIN_REQUIRED","Only the III Cell can configure the radius");
        var location=locations.findByInternshipId(ownInternship(studentId).getId()).orElseThrow(InternshipLocationService::notFound);
        if (location.getGeofenceRadiusMeters().compareTo(radius) == 0) return response(location);
        location.updateProposal(location.getLatitude(),location.getLongitude(),radius,clock.instant());
        return response(locations.saveAndFlush(location));
    }

    @Transactional(readOnly=true)
    public boolean fullyApproved(InternshipLocation location) {
        if(location!=null && location.getId()!=null) location=locations.findById(location.getId()).orElse(null);
        if(location==null || !location.dualApproved()) return false;
        var approvingMentor=location.getMentorReviewer().getId();
        return assignments.findByStudentIdAndActiveTrue(location.getInternship().getOnboarding().getStudent().getId()).filter(a -> a.getMentor().getId().equals(approvingMentor)).isPresent();
    }

    @Transactional
    public InternshipLocationResponse review(Long studentId,UserPrincipal principal,WorkplaceReview request) {
        var actor=users.findById(principal.id()).filter(User::isActive).orElseThrow(() -> error(HttpStatus.FORBIDDEN,"REVIEWER_REQUIRED","An active reviewer is required"));
        if(actor.getRole()==Role.MENTOR) getForMentor(studentId,principal);
        else if(actor.getRole()!=Role.ADMIN) throw error(HttpStatus.FORBIDDEN,"REVIEWER_REQUIRED","Mentor or III Cell role is required");
        var location=locations.findByInternshipId(ownInternship(studentId).getId()).orElseThrow(InternshipLocationService::notFound);
        if(location.getRevision()!=request.revision()) throw error(HttpStatus.CONFLICT,"WORKPLACE_REVISION_CHANGED","Refresh the changed workplace before reviewing");
        if(location.getWorkplaceAddress()==null) throw error(HttpStatus.CONFLICT,"MAP_REGISTRATION_REQUIRED","Register the actual workplace on the map before approval");
        if(request.decision()==WorkplaceReview.Decision.REJECT && (request.remarks()==null || request.remarks().strip().length()<5)) throw error(HttpStatus.BAD_REQUEST,"REJECTION_REASON_REQUIRED","Provide a rejection reason of at least five characters");
        location.review(actor,request.decision().name(),request.remarks(),clock.instant());locations.saveAndFlush(location);
        jdbc.update("insert into workplace_review_audit(location_id,revision,reviewer_id,reviewer_role,decision,reviewed_at,remarks,latitude,longitude,radius_meters,place_id,workplace_address) values (?,?,?,?,?,?,?,?,?,?,?,?)",location.getId(),location.getRevision(),actor.getId(),actor.getRole().name(),request.decision().name(),java.sql.Timestamp.from(clock.instant()),request.remarks(),location.getLatitude(),location.getLongitude(),location.getGeofenceRadiusMeters(),location.getPlaceId(),location.getWorkplaceAddress());
        if (request.decision() == WorkplaceReview.Decision.APPROVE) notifications.internshipApproved(location, actor, fullyApproved(location));
        return response(location);
    }

    private InternshipDetails ownInternship(Long studentId) {
        return internships.findByOnboardingStudentId(studentId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "INTERNSHIP_DETAILS_NOT_FOUND",
                        "Internship details were not found"));
    }

    private BigDecimal radius(InternshipLocationRequest request) {
        return request.geofenceRadiusMeters() == null ? defaultRadius : request.geofenceRadiusMeters();
    }

    private InternshipLocationResponse response(InternshipLocation location) {
        return new InternshipLocationResponse(location.getId(), location.getInternship().getId(),
                location.getLatitude(), location.getLongitude(), location.getGeofenceRadiusMeters(),
                location.getStatus()==InternshipLocationStatus.APPROVED && !fullyApproved(location) ? InternshipLocationStatus.PENDING : location.getStatus(), location.getCreatedAt(), location.getUpdatedAt(), location.getConfirmedAt(),
                location.getConfirmedBy() == null ? null : location.getConfirmedBy().getId(), location.getPlaceId(), location.getWorkplaceAddress(), location.getWorkplaceName(), location.getInternship().getCompanyName(), location.getRevision(),location.getMentorDecision(),location.getMentorReviewer()==null?null:location.getMentorReviewer().getId(),location.getMentorReviewedAt(),location.getMentorRemarks(),location.getIiicDecision(),location.getIiicRemarks());
    }

    private static AttendanceException notFound() {
        return error(HttpStatus.NOT_FOUND, "INTERNSHIP_LOCATION_NOT_FOUND", "Internship location has not been provided");
    }

    private static AttendanceException error(HttpStatus status, String code, String message) {
        return new AttendanceException(status, code, message);
    }
}
