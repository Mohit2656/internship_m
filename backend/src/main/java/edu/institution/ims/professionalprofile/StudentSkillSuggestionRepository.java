package edu.institution.ims.professionalprofile;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface StudentSkillSuggestionRepository extends JpaRepository<StudentSkillSuggestion, Long> {
    boolean existsByExtractedSkillId(Long extractedSkillId);
    List<StudentSkillSuggestion> findAllByResumeAnalysisIdAndReviewStatusOrderByDisplayNameAsc(Long analysisId, SkillReviewStatus status);
    Optional<StudentSkillSuggestion> findByIdAndProfessionalProfileStudentId(Long id, Long studentId);
}
