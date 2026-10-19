package edu.institution.ims.attendance;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.LocalDate;
import java.util.*;

/** Calendar exclusions until an institutional holiday/approved-leave workflow is available. */
@ConfigurationProperties(prefix = "app.attendance.calendar")
public class AttendanceCalendarProperties {
    private Set<LocalDate> holidays = new HashSet<>();
    private Map<Long, List<LeavePeriod>> approvedLeave = new HashMap<>();

    public Set<LocalDate> getHolidays() { return holidays; }
    public void setHolidays(Set<LocalDate> holidays) { this.holidays = holidays; }
    public Map<Long, List<LeavePeriod>> getApprovedLeave() { return approvedLeave; }
    public void setApprovedLeave(Map<Long, List<LeavePeriod>> approvedLeave) { this.approvedLeave = approvedLeave; }

    public boolean isExcluded(Long studentId, LocalDate date) {
        return holidays.contains(date) || approvedLeave.getOrDefault(studentId, List.of()).stream()
                .anyMatch(leave -> !date.isBefore(leave.start()) && !date.isAfter(leave.end()));
    }

    public record LeavePeriod(LocalDate start, LocalDate end) {
        public LeavePeriod {
            if (start == null || end == null || end.isBefore(start))
                throw new IllegalArgumentException("Approved leave requires an inclusive start/end date range");
        }
    }
}
