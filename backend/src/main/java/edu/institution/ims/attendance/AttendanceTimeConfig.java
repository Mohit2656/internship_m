package edu.institution.ims.attendance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.*;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(AttendanceCalendarProperties.class)
public class AttendanceTimeConfig {
    @Bean
    Clock attendanceClock(@Value("${app.attendance.zone:Asia/Kolkata}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
