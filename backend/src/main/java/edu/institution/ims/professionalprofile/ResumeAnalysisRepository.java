package edu.institution.ims.professionalprofile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.*;

public interface ResumeAnalysisRepository extends JpaRepository<ResumeAnalysis, Long> {
    Optional<ResumeAnalysis> findByResumeId(Long resumeId);
    @Query("select distinct a from ResumeAnalysis a left join fetch a.experiences where a.resume.id in :resumeIds")
    List<ResumeAnalysis> findAllWithExperiencesByResumeIdIn(@Param("resumeIds") Collection<Long> resumeIds);
}
