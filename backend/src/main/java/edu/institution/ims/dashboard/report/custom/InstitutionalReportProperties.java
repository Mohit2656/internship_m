package edu.institution.ims.dashboard.report.custom;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.institution")
public class InstitutionalReportProperties {
    private String name = "St. Vincent Pallotti College of Engineering and Technology";
    private String department = "Department of Computer Engineering";
    private String header = "INTERNSHIP MANAGEMENT REPORT";

    public String getName() { return name; }
    public void setName(String name) { if (name != null && !name.isBlank()) this.name = name; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { if (department != null && !department.isBlank()) this.department = department; }
    public String getHeader() { return header; }
    public void setHeader(String header) { if (header != null && !header.isBlank()) this.header = header; }
}
