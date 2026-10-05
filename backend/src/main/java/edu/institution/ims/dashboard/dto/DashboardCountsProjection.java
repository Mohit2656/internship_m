package edu.institution.ims.dashboard.dto;

public interface DashboardCountsProjection {
    Long getTotal(); Long getProfileComplete(); Long getProfileIncomplete(); Long getInternshipSecured(); Long getInternshipNotSecured();
    Long getDocumentsPending(); Long getDocumentsComplete(); Long getDiaryIssued(); Long getDetailsComplete(); Long getDiaryPending(); Long getDetailsPending();
}
