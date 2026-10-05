package edu.institution.ims.studentprofile.validation;

import jakarta.validation.*;
import java.lang.annotation.*;

@Documented @Constraint(validatedBy = AcademicYearValidator.class) @Target({ElementType.FIELD, ElementType.PARAMETER}) @Retention(RetentionPolicy.RUNTIME)
public @interface AcademicYear {
    String message() default "Academic year must use consecutive years in YYYY-YYYY format";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

