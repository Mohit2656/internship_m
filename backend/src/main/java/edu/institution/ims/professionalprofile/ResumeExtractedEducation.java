package edu.institution.ims.professionalprofile;

import jakarta.persistence.*;

@Entity @Table(name = "resume_extracted_education")
public class ResumeExtractedEducation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "analysis_id", nullable = false) private ResumeAnalysis analysis;
    @Column(length = 300) private String degree;
    @Column(length = 300) private String field;
    @Column(length = 400) private String institution;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    protected ResumeExtractedEducation() {}
    ResumeExtractedEducation(ResumeAnalysis analysis, AiEducation value, int sortOrder) {
        this.analysis = analysis; degree = value.degree(); field = value.field(); institution = value.institution(); this.sortOrder = sortOrder;
    }
    public String getDegree() { return degree; }
    public String getField() { return field; }
    public String getInstitution() { return institution; }
}
