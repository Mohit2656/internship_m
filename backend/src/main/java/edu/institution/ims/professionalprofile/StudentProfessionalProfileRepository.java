package edu.institution.ims.professionalprofile;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface StudentProfessionalProfileRepository extends JpaRepository<StudentProfessionalProfile, Long> {
    Optional<StudentProfessionalProfile> findByStudentId(Long studentId);
    List<StudentProfessionalProfile> findAllByStudentIdIn(Collection<Long> studentIds);
    boolean existsByStudentId(Long studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from StudentProfessionalProfile p where p.student.id = :studentId")
    Optional<StudentProfessionalProfile> findByStudentIdForUpdate(@Param("studentId") Long studentId);
}
