package edu.institution.ims.opportunity;

import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.studentprofile.*;
import edu.institution.ims.user.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service
public class OpportunityInterestService {
    public record InterestStatus(Long opportunityId, boolean interested) {}
    public record InterestedStudent(Long studentId, String studentName, String uid, Double matchPercentage) {}
    public record InterestedStudents(List<InterestedStudent> students) {}

    private final OpportunityRepository opportunities;
    private final OpportunityInterestRepository interests;
    private final UserRepository users;
    private final StudentProfileRepository profiles;
    private final SystemMatchedStudentService matches;

    public OpportunityInterestService(OpportunityRepository opportunities, OpportunityInterestRepository interests,
            UserRepository users, StudentProfileRepository profiles, SystemMatchedStudentService matches) {
        this.opportunities = opportunities; this.interests = interests; this.users = users;
        this.profiles = profiles; this.matches = matches;
    }

    @Transactional
    public InterestStatus express(UserPrincipal principal, Long opportunityId) {
        Opportunity opportunity = opportunities.findByIdForUpdate(opportunityId)
                .filter(value -> value.getStatus() == OpportunityStatus.PUBLISHED)
                .filter(value -> value.getApplicationDeadline() == null || !value.getApplicationDeadline().isBefore(LocalDate.now()))
                .orElseThrow(() -> new OpportunityException(HttpStatus.NOT_FOUND, "OPPORTUNITY_NOT_FOUND", "Active opportunity was not found"));
        if (!interests.existsByOpportunityIdAndStudentId(opportunityId, principal.id())) {
            User student = users.findById(principal.id()).filter(value -> value.getRole() == Role.STUDENT && value.isActive())
                    .orElseThrow(() -> new OpportunityException(HttpStatus.FORBIDDEN, "STUDENT_REQUIRED", "An active student account is required"));
            interests.save(new OpportunityInterest(opportunity, student));
        }
        return new InterestStatus(opportunityId, true);
    }

    @Transactional(readOnly = true)
    public Set<Long> ownInterests(UserPrincipal principal) {
        Set<Long> ids = new LinkedHashSet<>();
        interests.findAllByStudentId(principal.id()).forEach(value -> ids.add(value.getOpportunity().getId()));
        return ids;
    }

    @Transactional(readOnly = true)
    public InterestedStudents forOpportunity(Long opportunityId) {
        if (!opportunities.existsById(opportunityId))
            throw new OpportunityException(HttpStatus.NOT_FOUND, "OPPORTUNITY_NOT_FOUND", "Opportunity was not found");
        List<OpportunityInterest> rows = interests.findAllByOpportunityIdOrderByCreatedAtAscIdAsc(opportunityId);
        List<Long> studentIds = rows.stream().map(value -> value.getStudent().getId()).toList();
        Map<Long, StudentProfile> byStudent = new HashMap<>();
        profiles.findAllByUserIdIn(studentIds).forEach(profile -> byStudent.put(profile.getUser().getId(), profile));
        Map<Long, Double> percentages = matches.matchPercentages(opportunityId, studentIds);
        List<InterestedStudent> result = rows.stream().map(row -> {
            Long studentId = row.getStudent().getId();
            StudentProfile profile = byStudent.get(studentId);
            return new InterestedStudent(studentId,
                    profile == null ? row.getStudent().getEmail() : profile.getStudentName(),
                    profile == null ? null : profile.getUid(), percentages.get(studentId));
        }).toList();
        return new InterestedStudents(result);
    }
}

