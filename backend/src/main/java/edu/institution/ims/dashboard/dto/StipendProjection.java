package edu.institution.ims.dashboard.dto;

import java.math.BigDecimal;

public interface StipendProjection { Long getPaid(); Long getUnpaid(); BigDecimal getAveragePaidMonthlyStipend(); }
