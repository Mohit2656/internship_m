package edu.institution.ims.internship;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.assertj.core.api.Assertions.assertThat;

public class InternshipDetailsRepositoryQueryTest {
    @Test
    void testQueryAnnotationContainsExclusions() throws Exception {
        Method method = InternshipDetailsRepository.class.getMethod("findEligibleForAbsenceProcessing", java.time.LocalDate.class);
        org.springframework.data.jpa.repository.Query query = method.getAnnotation(org.springframework.data.jpa.repository.Query.class);
        assertThat(query.value()).contains("TERMINATED_EARLY");
        assertThat(query.value()).contains("deletedAt is null");
    }
}
