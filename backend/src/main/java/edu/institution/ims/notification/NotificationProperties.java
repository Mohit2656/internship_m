package edu.institution.ims.notification;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.*;
@Component
@ConfigurationProperties(prefix="app.notifications")
public class NotificationProperties {
    private List<MonthlyDeadline> monthlyReportDeadlines = new ArrayList<>();
    public List<MonthlyDeadline> getMonthlyReportDeadlines() { return monthlyReportDeadlines; }
    public void setMonthlyReportDeadlines(List<MonthlyDeadline> values) { monthlyReportDeadlines=values; }
    public record MonthlyDeadline(Long internshipId, int monthNumber, LocalDate dueDate) {
        public MonthlyDeadline { if(internshipId==null || monthNumber<1 || dueDate==null) throw new IllegalArgumentException("Monthly deadline requires internshipId, positive monthNumber and dueDate"); }
    }
}
