package edu.institution.ims.dashboard.report.custom;

import edu.institution.ims.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "saved_report_configurations", indexes = {
    @Index(name = "idx_saved_report_config_creator", columnList = "created_by, updated_at")
})
public class SavedReportConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "scope", nullable = false, length = 30)
    private String scope = "INTERNSHIP";

    @Column(name = "configuration_json", nullable = false, columnDefinition = "TEXT")
    private String configurationJson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SavedReportConfiguration() {}

    public SavedReportConfiguration(String title, String description, String scope, String configurationJson, User createdBy) {
        this.title = title;
        this.description = description;
        this.scope = scope == null || scope.isBlank() ? "INTERNSHIP" : scope;
        this.configurationJson = configurationJson;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onPrePersist() {
        var now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onPreUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getConfigurationJson() { return configurationJson; }
    public void setConfigurationJson(String configurationJson) { this.configurationJson = configurationJson; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
