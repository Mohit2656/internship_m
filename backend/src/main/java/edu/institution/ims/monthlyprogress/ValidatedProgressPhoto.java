package edu.institution.ims.monthlyprogress;

import java.math.BigDecimal;
import java.time.Instant;

public record ValidatedProgressPhoto(String originalName, String extension, String mimeType, long size,
                                     Instant capturedAt, BigDecimal latitude, BigDecimal longitude) {}
