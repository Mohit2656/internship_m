package edu.institution.ims.opportunity;

import edu.institution.ims.professionalprofile.NormalizedSkill;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "opportunity_skills", uniqueConstraints = @UniqueConstraint(name = "uk_opportunity_skills_normalized", columnNames = {"opportunity_id", "requirement_type", "normalized_name"}))
public class OpportunitySkill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "opportunity_id", nullable = false) private Opportunity opportunity;
    @Column(name = "display_name", nullable = false, length = 100) private String displayName;
    @Column(name = "normalized_name", nullable = false, length = 100) private String normalizedName;
    @Enumerated(EnumType.STRING) @Column(name = "requirement_type", nullable = false, length = 20) private OpportunitySkillType requirementType;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected OpportunitySkill() {}
    OpportunitySkill(Opportunity opportunity, NormalizedSkill skill, OpportunitySkillType type) {
        this.opportunity = opportunity; displayName = skill.displayName(); normalizedName = skill.normalizedName();
        requirementType = type; createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getParentOpportunityId() { return opportunity.getId(); }
    public String getDisplayName() { return displayName; }
    public String getNormalizedName() { return normalizedName; }
    public OpportunitySkillType getRequirementType() { return requirementType; }
}
