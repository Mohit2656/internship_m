package edu.institution.ims.internship;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface InternshipDocumentRepository extends JpaRepository<InternshipDocument, Long> {
    List<InternshipDocument> findAllByOnboardingIdOrderByDocumentType(Long onboardingId);
    Optional<InternshipDocument> findByOnboardingIdAndDocumentType(Long onboardingId, DocumentType documentType);
    long countByOnboardingId(Long onboardingId);
}
