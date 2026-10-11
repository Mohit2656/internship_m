package edu.institution.ims.monthlyprogress;

import java.time.LocalDate;
import java.util.List;

public record MonthlyProgressOverviewResponse(Long internshipId, String companyName, String internshipMode,
                                              LocalDate internshipStart, LocalDate internshipEnd,
                                              List<MonthlyProgressPeriodResponse> periods, long maxPhotoSizeBytes) {}
