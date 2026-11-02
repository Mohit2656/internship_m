package edu.institution.ims.studentprofile;

import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long>, JpaSpecificationExecutor<StudentProfile> {
    Optional<StudentProfile> findByUserId(Long userId);
    @Query("select p from StudentProfile p join fetch p.user u where u.role = edu.institution.ims.user.Role.STUDENT and u.active = true")
    List<StudentProfile> findAllActiveStudentCandidates();
    boolean existsByUserId(Long userId);
    boolean existsByAcademicYearAndSectionAndRollNumberIgnoreCase(String academicYear, String section, String rollNumber);
    boolean existsByAcademicYearAndSectionAndRollNumberIgnoreCaseAndIdNot(String academicYear, String section, String rollNumber, Long id);
    boolean existsByUidIgnoreCase(String uid);
    boolean existsByInstituteEmailIgnoreCase(String email);
    boolean existsByBranchAndAcademicYearAndRollNumberIgnoreCase(Branch branch, String academicYear, String rollNumber);
    boolean existsByUidIgnoreCaseAndIdNot(String uid, Long id);
    boolean existsByInstituteEmailIgnoreCaseAndIdNot(String email, Long id);
    boolean existsByBranchAndAcademicYearAndRollNumberIgnoreCaseAndIdNot(Branch branch, String academicYear, String rollNumber, Long id);
}
