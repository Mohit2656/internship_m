package edu.institution.ims.attendance;
import jakarta.validation.constraints.*;
public record WorkplaceReview(@Min(1) long revision,@NotNull Decision decision,@Size(max=500) String remarks) { public enum Decision { APPROVE, REJECT } }
