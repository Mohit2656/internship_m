package edu.institution.ims.professionalprofile;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface StudentSkillRepository extends JpaRepository<StudentSkill, Long> {
    @org.springframework.data.jpa.repository.Query("select max(s.updatedAt) from StudentSkill s where s.professionalProfile.id = :profileId")
    java.time.Instant lastStudentUpdateByProfileId(@org.springframework.data.repository.query.Param("profileId") Long profileId);
    List<StudentSkill> findAllByProfessionalProfileIdAndActiveTrueOrderByDisplayNameAsc(Long profileId);
    List<StudentSkill> findAllByProfessionalProfileIdInAndActiveTrue(Collection<Long> profileIds);
    Optional<StudentSkill> findByProfessionalProfileIdAndNormalizedNameAndActiveTrue(Long profileId, String normalizedName);
    Optional<StudentSkill> findByIdAndProfessionalProfileStudentIdAndActiveTrue(Long id, Long studentId);
}
