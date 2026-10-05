package edu.institution.ims.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.DayOfWeek;
import java.util.*;

public interface HybridWorkScheduleRepository extends JpaRepository<HybridWorkSchedule, Long> {
    Optional<HybridWorkSchedule> findByInternshipIdAndDayOfWeek(Long internshipId, DayOfWeek dayOfWeek);
    List<HybridWorkSchedule> findAllByInternshipId(Long internshipId);
    void deleteAllByInternshipId(Long internshipId);
}
