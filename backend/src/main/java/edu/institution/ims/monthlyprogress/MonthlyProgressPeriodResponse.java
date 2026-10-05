package edu.institution.ims.monthlyprogress;

import java.time.LocalDate;

public record MonthlyProgressPeriodResponse(int monthNumber, LocalDate periodStart, LocalDate periodEnd,
                                            String status, MonthlyProgressReportResponse report) {}
