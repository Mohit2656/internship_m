package edu.institution.ims.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.LoggerFactory;
import java.time.*;
import java.time.temporal.ChronoUnit;

@Component
@ConditionalOnProperty(name="app.notifications.reminders.enabled",havingValue="true",matchIfMissing=true)
public class DeadlineNotificationScheduler {
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final NotificationProperties settings;
    private final Clock clock;
    public DeadlineNotificationScheduler(JdbcTemplate jdbc, NotificationService notifications, NotificationProperties settings, Clock clock) {
        this.jdbc=jdbc; this.notifications=notifications; this.settings=settings; this.clock=clock;
    }
    @Scheduled(initialDelayString="${app.notifications.reminders.initial-delay-ms:10000}",fixedDelayString="${app.notifications.reminders.fixed-delay-ms:900000}")
    public void run() {
        LocalDate today=LocalDate.now(clock);
        for(var deadline:settings.getMonthlyReportDeadlines()) {
            try {
                String stage=stage(today,deadline.dueDate()); if(stage==null) continue;
                var owners=jdbc.query("select o.student_user_id,i.start_date,i.end_date from internship_details i join internship_onboarding o on o.id=i.onboarding_id "
                        + "join users u on u.id=o.student_user_id where i.id=? and u.active=true and u.role='STUDENT' and o.workflow_status='INTERNSHIP_DETAILS_COMPLETE' "
                        + "and not exists(select 1 from monthly_progress_reports r where r.student_internship_id=i.id "
                        + "and r.report_month_number=? and r.deleted_at is null and r.review_status<>'REJECTED')",
                        (rs,n) -> new MonthlyCandidate(rs.getLong("student_user_id"),rs.getDate("start_date").toLocalDate(),rs.getDate("end_date").toLocalDate()),deadline.internshipId(),deadline.monthNumber());
                for(var candidate:owners) {
                    LocalDate periodStart=candidate.start().plusMonths(deadline.monthNumber()-1L);
                    if(periodStart.isAfter(candidate.end()) || periodStart.isAfter(today) || deadline.dueDate().isBefore(periodStart)) continue;
                    remind(candidate.owner(),"monthly:"+deadline.internshipId()+":"+deadline.monthNumber(),
                        "Monthly Progress Report - Month "+deadline.monthNumber(),deadline.dueDate(),stage,"/student/monthly-progress#month-"+deadline.monthNumber());
                }
            } catch(RuntimeException failure) { LoggerFactory.getLogger(getClass()).error("Monthly deadline reminder failed; will retry",failure); }
        }
        var students=jdbc.queryForList("select id from users where role='STUDENT' and active=true",Long.class);
        jdbc.query("select id,title,application_deadline from opportunities where status='PUBLISHED' and application_deadline between ? and ?", rs -> {
            LocalDate due=rs.getDate("application_deadline").toLocalDate(); String stage=stage(today,due);
            if(stage!=null) for(Long owner:students) {
                try { remind(owner,"opportunity:"+rs.getLong("id"),rs.getString("title"),due,stage,
                        due.isBefore(today)?"/student/opportunities":"/student/opportunities/"+rs.getLong("id")); }
                catch(RuntimeException failure) { LoggerFactory.getLogger(getClass()).error("Opportunity deadline reminder failed; will retry",failure); }
            }
        },java.sql.Date.valueOf(today.minusDays(1)),java.sql.Date.valueOf(today.plusDays(7)));
    }
    private record MonthlyCandidate(Long owner, LocalDate start, LocalDate end) {}
    private void remind(Long owner,String key,String title,LocalDate due,String stage,String url) {
        notifications.create(owner,key+":"+due+":"+stage,"DEADLINE_REMINDER",title+" - "+stage.replace('_',' ').toLowerCase(),
                "Deadline: "+due+" ("+clock.getZone().getId()+"). "+switch(stage) {
                    case "DUE_TODAY" -> "This deadline is due today.";
                    case "OVERDUE" -> "This deadline has passed.";
                    case "APPROACHING" -> "The deadline is approaching.";
                    default -> "An upcoming deadline needs your attention.";
                },null,null,url,clock.instant());
    }
    static String stage(LocalDate today,LocalDate due) {
        long days=ChronoUnit.DAYS.between(today,due);
        return days>3 && days<=7?"UPCOMING":days>0 && days<=3?"APPROACHING":days==0?"DUE_TODAY":days==-1?"OVERDUE":null;
    }
}
