package edu.institution.ims.mentor;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MentorStudentAssignmentRepository extends JpaRepository<MentorStudentAssignment, Long> {
    @EntityGraph(attributePaths = {"mentor", "student", "student.studentProfile", "student.studentAccount"})
    List<MentorStudentAssignment> findAllByActiveTrue();

    @EntityGraph(attributePaths = {"student", "student.studentProfile", "student.studentAccount"})
    List<MentorStudentAssignment> findAllByMentorIdAndActiveTrueOrderByStudentEmailAsc(Long mentorUserId);

    Optional<MentorStudentAssignment> findByStudentIdAndActiveTrue(Long studentUserId);

    long countByMentorIdAndActiveTrue(Long mentorUserId);

    List<MentorStudentAssignment> findAllByStudentIdOrderByAssignedAtAsc(Long studentUserId);
}
