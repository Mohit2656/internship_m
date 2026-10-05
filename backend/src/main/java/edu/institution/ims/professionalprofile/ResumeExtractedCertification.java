package edu.institution.ims.professionalprofile;

import jakarta.persistence.*;

@Entity @Table(name = "resume_extracted_certifications")
public class ResumeExtractedCertification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "analysis_id", nullable = false) private ResumeAnalysis analysis;
    @Column(nullable = false, length = 400) private String name;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    protected ResumeExtractedCertification() {}
    ResumeExtractedCertification(ResumeAnalysis analysis, String name, int sortOrder) { this.analysis = analysis; this.name = name; this.sortOrder = sortOrder; }
    public String getName() { return name; }
}
