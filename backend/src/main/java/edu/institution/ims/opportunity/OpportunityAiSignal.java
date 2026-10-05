package edu.institution.ims.opportunity;

import jakarta.persistence.*;

@Entity
@Table(name = "opportunity_ai_signals")
public class OpportunityAiSignal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "analysis_id", nullable = false) private OpportunityAiAnalysis analysis;
    @Enumerated(EnumType.STRING) @Column(name = "signal_type", nullable = false, length = 30) private OpportunityAiSignalType signalType;
    @Column(name = "display_value", nullable = false, length = 300) private String displayValue;
    @Column(name = "normalized_value", nullable = false, length = 300) private String normalizedValue;
    @Column(name = "sort_order", nullable = false) private int sortOrder;

    protected OpportunityAiSignal() {}
    OpportunityAiSignal(OpportunityAiAnalysis analysis, OpportunityAiSignalType type, String value, int sortOrder) {
        this.analysis = analysis; signalType = type; displayValue = value.trim().replaceAll("\\s+", " ");
        normalizedValue = displayValue.toLowerCase(java.util.Locale.ROOT); this.sortOrder = sortOrder;
    }
    public OpportunityAiSignalType getSignalType() { return signalType; }
    public String getDisplayValue() { return displayValue; }
}
