package edu.institution.ims.studentprofile.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class AcademicYearValidator implements ConstraintValidator<AcademicYear, String> {
    @Override public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || !value.matches("\\d{4}-\\d{4}")) return false;
        return Integer.parseInt(value.substring(5)) == Integer.parseInt(value.substring(0, 4)) + 1;
    }
}

