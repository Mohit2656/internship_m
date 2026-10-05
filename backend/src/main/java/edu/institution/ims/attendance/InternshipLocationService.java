package edu.institution.ims.attendance;

import edu.institution.ims.internship.InternshipDetails;
import edu.institution.ims.internship.InternshipDetailsRepository;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
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

    public InternshipLocationService(InternshipLocationRepository locations,
            InternshipDetailsRepository internships, UserRepository users, Clock clock,
            @Value("${app.attendance.default-geofence-radius-meters:200}") BigDecimal defaultRadius) {
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
        InternshipDetails internship = ownInternship(principal.id());
        var current = locations.findByInternshipId(internship.getId());
        if (current.isPresent() && current.get().getStatus() == InternshipLocationStatus.CONFIRMED) {
            throw error(HttpStatus.CONFLICT, "LOCATION_ALREADY_CONFIRMED",
                    "Only the III Cell can change a confirmed internship location");
        }
        InternshipLocation location = current.orElseGet(() -> new InternshipLocation(internship,
                request.latitude(), request.longitude(), radius(request), clock.instant()));
        if (current.isPresent()) {
            location.updateProposal(request.latitude(), request.longitude(), radius(request), clock.instant());
        }
        return response(locations.saveAndFlush(location));
    }

    @Transactional(readOnly = true)
    public InternshipLocationResponse getForAdmin(Long studentId) {
        InternshipDetails internship = ownInternship(studentId);
        return response(locations.findByInternshipId(internship.getId()).orElseThrow(InternshipLocationService::notFound));
    }

    @Transactional
    public InternshipLocationResponse updateForAdmin(Long studentId, InternshipLocationRequest request) {
        InternshipDetails internship = ownInternship(studentId);
        InternshipLocation location = locations.findByInternshipId(internship.getId())
                .orElseGet(() -> new InternshipLocation(internship, request.latitude(), request.longitude(),
                        radius(request), clock.instant()));
        if (location.getId() != null) {
            location.updateProposal(request.latitude(), request.longitude(), radius(request), clock.instant());
        }
        return response(locations.saveAndFlush(location));
    }

    @Transactional
    public InternshipLocationResponse confirmForAdmin(Long studentId, UserPrincipal principal) {
        User admin = users.findById(principal.id()).filter(user -> user.getRole() == Role.ADMIN)
                .orElseThrow(() -> error(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "Only the III Cell can confirm locations"));
        InternshipDetails internship = ownInternship(studentId);
        InternshipLocation location = locations.findByInternshipId(internship.getId())
                .orElseThrow(InternshipLocationService::notFound);
        location.confirm(admin, clock.instant());
        return response(locations.saveAndFlush(location));
    }

    @Transactional
    public InternshipLocation proposeFromAttendanceIfMissing(InternshipDetails internship,
            BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) return null;
        var current = locations.findByInternshipId(internship.getId());
        if (current.isPresent()) return current.get();
        try {
            return locations.saveAndFlush(new InternshipLocation(internship, latitude, longitude,
                    defaultRadius, clock.instant()));
        } catch (DataIntegrityViolationException race) {
            return locations.findByInternshipId(internship.getId()).orElseThrow(() -> race);
        }
    }

    private InternshipDetails ownInternship(Long studentId) {
        return internships.findByOnboardingStudentId(studentId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "INTERNSHIP_DETAILS_NOT_FOUND",
                        "Internship details were not found"));
    }

    private BigDecimal radius(InternshipLocationRequest request) {
        return request.geofenceRadiusMeters() == null ? defaultRadius : request.geofenceRadiusMeters();
    }

    static InternshipLocationResponse response(InternshipLocation location) {
        return new InternshipLocationResponse(location.getId(), location.getInternship().getId(),
                location.getLatitude(), location.getLongitude(), location.getGeofenceRadiusMeters(),
                location.getStatus(), location.getCreatedAt(), location.getUpdatedAt(), location.getConfirmedAt(),
                location.getConfirmedBy() == null ? null : location.getConfirmedBy().getId());
    }

    private static AttendanceException notFound() {
        return error(HttpStatus.NOT_FOUND, "INTERNSHIP_LOCATION_NOT_FOUND", "Internship location has not been provided");
    }

    private static AttendanceException error(HttpStatus status, String code, String message) {
        return new AttendanceException(status, code, message);
    }
}
