package edu.institution.ims.professionalprofile;

import jakarta.persistence.*;

@Entity @Table(name = "resume_extracted_experiences")
public class ResumeExtractedExperience {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "analysis_id", nullable = false) private ResumeAnalysis analysis;
    @Column(length = 300) private String title;
    @Column(length = 300) private String company;
    @Column(name = "start_date", length = 100) private String startDate;
    @Column(name = "end_date", length = 100) private String endDate;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    protected ResumeExtractedExperience() {}
    ResumeExtractedExperience(ResumeAnalysis analysis, AiExperience value, int sortOrder) {
        this.analysis = analysis; title = value.title(); company = value.company(); startDate = value.startDate(); endDate = value.endDate(); this.sortOrder = sortOrder;
    }
    public String getTitle() { return title; }
    public String getCompany() { return company; }
    public String getStartDate() { return startDate; }
    public String getEndDate() { return endDate; }
}
