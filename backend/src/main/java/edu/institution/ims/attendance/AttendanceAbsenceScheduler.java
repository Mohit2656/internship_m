package edu.institution.ims.attendance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.attendance.absence.enabled", havingValue = "true", matchIfMissing = true)
public class AttendanceAbsenceScheduler {
    private static final Logger log = LoggerFactory.getLogger(AttendanceAbsenceScheduler.class);
    private final AttendanceAbsenceService service;

    public AttendanceAbsenceScheduler(AttendanceAbsenceService service) { this.service = service; }

    // Starts automatically, then retries every five minutes, including after backend downtime.
    @Scheduled(initialDelayString = "${app.attendance.absence.initial-delay-ms:10000}",
            fixedDelayString = "${app.attendance.absence.fixed-delay-ms:300000}")
    @Scheduled(cron = "0 0 12 * * *", zone = "${app.attendance.zone:Asia/Kolkata}")
    public void process() {
        int created = service.processOverdueAttendance();
        if (created > 0) log.info("Created {} automatic daily absence records", created);
    }
}
