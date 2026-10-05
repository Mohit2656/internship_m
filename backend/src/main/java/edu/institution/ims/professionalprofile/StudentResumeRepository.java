package edu.institution.ims.professionalprofile;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface StudentResumeRepository extends JpaRepository<StudentResume, Long> {
    Optional<StudentResume> findByProfessionalProfileIdAndActiveTrue(Long professionalProfileId);
    List<StudentResume> findAllByProfessionalProfileIdInAndActiveTrue(Collection<Long> professionalProfileIds);
    long countByProfessionalProfileIdAndActiveTrue(Long professionalProfileId);
    List<StudentResume> findAllByProfessionalProfileIdOrderByUploadedAtAsc(Long professionalProfileId);
}
