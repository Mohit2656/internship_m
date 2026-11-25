package edu.institution.ims.dashboard.report.custom;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SavedReportConfigurationRepository extends JpaRepository<SavedReportConfiguration, Long> {
    List<SavedReportConfiguration> findAllByOrderByUpdatedAtDesc();
}
