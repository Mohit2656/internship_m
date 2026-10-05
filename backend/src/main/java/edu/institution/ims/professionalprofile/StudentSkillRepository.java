package edu.institution.ims.professionalprofile;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface StudentSkillRepository extends JpaRepository<StudentSkill, Long> {
    List<StudentSkill> findAllByProfessionalProfileIdAndActiveTrueOrderByDisplayNameAsc(Long profileId);
    List<StudentSkill> findAllByProfessionalProfileIdInAndActiveTrue(Collection<Long> profileIds);
    Optional<StudentSkill> findByProfessionalProfileIdAndNormalizedNameAndActiveTrue(Long profileId, String normalizedName);
    Optional<StudentSkill> findByIdAndProfessionalProfileStudentIdAndActiveTrue(Long id, Long studentId);
}
