package edu.institution.ims.opportunity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "opportunity_ai_analyses")
public class OpportunityAiAnalysis {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "opportunity_id", nullable = false) private Opportunity opportunity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private OpportunityAiAnalysisStatus status;
    @Column(name = "input_hash", nullable = false, length = 64) private String inputHash;
    @Column(name = "model_name", length = 200) private String modelName;
    @Column(name = "model_version", length = 200) private String modelVersion;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "failure_reason_safe", length = 100) private String failureReasonSafe;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("suggestedRequirementType ASC, displayName ASC") private List<OpportunityAiSkillSuggestion> suggestions = new ArrayList<>();
    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("signalType ASC, sortOrder ASC") private List<OpportunityAiSignal> signals = new ArrayList<>();

    protected OpportunityAiAnalysis() {}
    OpportunityAiAnalysis(Opportunity opportunity, String inputHash) {
        this.opportunity = opportunity; this.inputHash = inputHash; status = OpportunityAiAnalysisStatus.PENDING;
        createdAt = Instant.now(); updatedAt = createdAt; startedAt = createdAt;
    }

    void complete(AiOpportunityResult result, List<OpportunityAiSkillSuggestion> values, List<OpportunityAiSignal> signalValues) {
        if (status != OpportunityAiAnalysisStatus.PENDING) throw new IllegalStateException("Opportunity analysis is not pending");
        modelName = result.modelName(); modelVersion = result.modelVersion(); suggestions.addAll(values); signals.addAll(signalValues);
        status = OpportunityAiAnalysisStatus.ANALYZED; completedAt = Instant.now(); updatedAt = completedAt;
    }
    void fail(String reason) {
        if (status == OpportunityAiAnalysisStatus.PENDING) {
            status = OpportunityAiAnalysisStatus.FAILED; failureReasonSafe = reason; completedAt = Instant.now(); updatedAt = completedAt;
        }
    }
    public Long getId() { return id; }
    public Opportunity getOpportunity() { return opportunity; }
    public OpportunityAiAnalysisStatus getStatus() { return status; }
    public String getInputHash() { return inputHash; }
    public String getModelName() { return modelName; }
    public String getModelVersion() { return modelVersion; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getFailureReasonSafe() { return failureReasonSafe; }
    public List<OpportunityAiSkillSuggestion> getSuggestions() { return List.copyOf(suggestions); }
    public List<OpportunityAiSignal> getSignals() { return List.copyOf(signals); }
}
