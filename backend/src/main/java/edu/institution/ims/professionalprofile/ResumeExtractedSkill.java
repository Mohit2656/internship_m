package edu.institution.ims.professionalprofile;

import jakarta.persistence.*;

@Entity @Table(name = "resume_extracted_skills")
public class ResumeExtractedSkill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "analysis_id", nullable = false) private ResumeAnalysis analysis;
    @Column(nullable = false, length = 200) private String name;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    protected ResumeExtractedSkill() {}
    ResumeExtractedSkill(ResumeAnalysis analysis, String name, int sortOrder) { this.analysis = analysis; this.name = name; this.sortOrder = sortOrder; }
    public Long getId() { return id; }
    public String getName() { return name; }
}
