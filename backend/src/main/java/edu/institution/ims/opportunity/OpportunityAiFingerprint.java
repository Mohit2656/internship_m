package edu.institution.ims.opportunity;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

final class OpportunityAiFingerprint {
    private OpportunityAiFingerprint() {}
    static String of(Opportunity opportunity) {
        String input = normalize(opportunity.getTitle()) + "\n" + normalize(opportunity.getDescription());
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
