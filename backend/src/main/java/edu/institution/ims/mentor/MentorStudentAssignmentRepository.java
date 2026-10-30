package edu.institution.ims.mentor;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface MentorStudentAssignmentRepository extends JpaRepository<MentorStudentAssignment, Long> {
    @EntityGraph(attributePaths = {"mentor", "student", "student.studentProfile", "student.studentAccount"})
    List<MentorStudentAssignment> findAllByActiveTrue();

    @EntityGraph(attributePaths = {"student", "student.studentProfile", "student.studentAccount"})
    List<MentorStudentAssignment> findAllByMentorIdAndActiveTrueOrderByStudentEmailAsc(Long mentorUserId);

    Optional<MentorStudentAssignment> findByStudentIdAndActiveTrue(Long studentUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select assignment from MentorStudentAssignment assignment "
            + "where assignment.student.id = :studentId and assignment.active = true")
    Optional<MentorStudentAssignment> findByStudentIdAndActiveTrueForLateAttendanceUpdate(
            @Param("studentId") Long studentId);

    @Query("select count(a) from MentorStudentAssignment a where a.mentor.id=:mentorUserId and a.active=true and a.student.active=true")
    long countByMentorIdAndActiveTrue(@Param("mentorUserId") Long mentorUserId);

    List<MentorStudentAssignment> findAllByStudentIdOrderByAssignedAtAsc(Long studentUserId);
}
