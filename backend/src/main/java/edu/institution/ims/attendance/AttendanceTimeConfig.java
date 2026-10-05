package edu.institution.ims.attendance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import java.time.*;

@Configuration
public class AttendanceTimeConfig {
    @Bean
    Clock attendanceClock(@Value("${app.attendance.zone:Asia/Kolkata}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
