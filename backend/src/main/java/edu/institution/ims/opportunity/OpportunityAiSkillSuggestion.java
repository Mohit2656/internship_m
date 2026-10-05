package edu.institution.ims.opportunity;

import edu.institution.ims.professionalprofile.NormalizedSkill;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "opportunity_ai_skill_suggestions")
public class OpportunityAiSkillSuggestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "analysis_id", nullable = false) private OpportunityAiAnalysis analysis;
    @Column(name = "raw_name", nullable = false, length = 100) private String rawName;
    @Column(name = "display_name", nullable = false, length = 100) private String displayName;
    @Column(name = "normalized_name", nullable = false, length = 100) private String normalizedName;
    @Enumerated(EnumType.STRING) @Column(name = "suggested_requirement_type", nullable = false, length = 20) private OpportunitySkillType suggestedRequirementType;
    @Enumerated(EnumType.STRING) @Column(name = "review_status", nullable = false, length = 20) private OpportunityAiReviewStatus reviewStatus;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "confirmed_skill_id") private OpportunitySkill confirmedSkill;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected OpportunityAiSkillSuggestion() {}
    OpportunityAiSkillSuggestion(OpportunityAiAnalysis analysis, String rawName, NormalizedSkill skill, OpportunitySkillType type) {
        this.analysis = analysis; this.rawName = rawName; displayName = skill.displayName(); normalizedName = skill.normalizedName();
        suggestedRequirementType = type; reviewStatus = OpportunityAiReviewStatus.PENDING; createdAt = Instant.now(); updatedAt = createdAt;
    }
    void confirm(OpportunitySkill skill) { reviewStatus = OpportunityAiReviewStatus.CONFIRMED; confirmedSkill = skill; updatedAt = Instant.now(); }
    void reject() { reviewStatus = OpportunityAiReviewStatus.REJECTED; updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public OpportunityAiAnalysis getAnalysis() { return analysis; }
    public String getRawName() { return rawName; }
    public String getDisplayName() { return displayName; }
    public String getNormalizedName() { return normalizedName; }
    public OpportunitySkillType getSuggestedRequirementType() { return suggestedRequirementType; }
    public OpportunityAiReviewStatus getReviewStatus() { return reviewStatus; }
}
