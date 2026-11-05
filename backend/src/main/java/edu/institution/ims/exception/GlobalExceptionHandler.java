package edu.institution.ims.exception;

import edu.institution.ims.common.ApiError;
import edu.institution.ims.studentprofile.ProfileConflictException;
import edu.institution.ims.studentprofile.ProfileNotFoundException;
import edu.institution.ims.internship.OnboardingException;
import edu.institution.ims.internship.InternshipDetailsException;
import edu.institution.ims.mentor.MentorAccountConflictException;
import edu.institution.ims.mentor.MentorAssignmentException;
import edu.institution.ims.monthlyprogress.MonthlyProgressException;
import edu.institution.ims.attendance.AttendanceException;
import edu.institution.ims.professionalprofile.ResumeException;
import edu.institution.ims.opportunity.OpportunityException;
import org.springframework.http.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    ResponseEntity<ApiError> workflowStatus(org.springframework.web.server.ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(new ApiError(Instant.now(),e.getStatusCode().value(),"WORKFLOW_REQUEST_FAILED",e.getReason(),null));
    }
    @ExceptionHandler(edu.institution.ims.reports.ReportPdfText.UnrenderableTextException.class)
    ResponseEntity<ApiError> reportText(edu.institution.ims.reports.ReportPdfText.UnrenderableTextException e) { return error(HttpStatus.UNPROCESSABLE_ENTITY, "REPORT_TEXT_UNSUPPORTED", e.getMessage()); }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class) ResponseEntity<ApiError> denied() { return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to access this resource"); }
    @ExceptionHandler(edu.institution.ims.auth.PasswordResetException.class) ResponseEntity<ApiError> passwordReset(edu.institution.ims.auth.PasswordResetException e) { return error(e.getStatus(), "PASSWORD_RESET_FAILED", e.getMessage()); }
    @ExceptionHandler(BadCredentialsException.class) ResponseEntity<ApiError> badCredentials() { return error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect"); }
    @ExceptionHandler(InactiveAccountException.class) ResponseEntity<ApiError> inactive(InactiveAccountException e) { return error(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", e.getMessage()); }
    @ExceptionHandler(DuplicateEmailException.class) ResponseEntity<ApiError> duplicate(DuplicateEmailException e) { return error(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", e.getMessage()); }
    @ExceptionHandler(MentorAccountConflictException.class) ResponseEntity<ApiError> mentorConflict(MentorAccountConflictException e) { return error(HttpStatus.CONFLICT, e.getCode(), e.getMessage()); }
    @ExceptionHandler(MentorAssignmentException.class) ResponseEntity<ApiError> mentorAssignment(MentorAssignmentException e) { return error(e.getStatus(), e.getCode(), e.getMessage()); }
    @ExceptionHandler(AttendanceException.class) ResponseEntity<ApiError> attendance(AttendanceException e) { return error(e.getStatus(), e.getCode(), e.getMessage()); }
    @ExceptionHandler(ResumeException.class) ResponseEntity<ApiError> resume(ResumeException e) { return error(e.getStatus(), e.getCode(), e.getMessage()); }
    @ExceptionHandler(OpportunityException.class) ResponseEntity<ApiError> opportunity(OpportunityException e) { return error(e.getStatus(), e.getCode(), e.getMessage()); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> dataIntegrity(DataIntegrityViolationException e) {
        log.error("DataIntegrityViolationException caught: ", e);
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (msg.contains("attendance") || msg.contains("uk_attendance")) {
            return error(HttpStatus.CONFLICT, "ATTENDANCE_ALREADY_SUBMITTED", "Today's attendance has already been submitted");
        }
        if (msg.contains("email") || msg.contains("users_email")) {
            return error(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "An account with this email already exists");
        }
        return error(HttpStatus.CONFLICT, "DATA_CONFLICT", "A conflicting record already exists");
    }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiError> illegalState(IllegalStateException e) {
        log.error("IllegalStateException caught: ", e);
        return error(HttpStatus.CONFLICT, "INVALID_STATE", e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : "Operation could not be completed in current state");
    }
    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> missingParam(org.springframework.web.bind.MissingServletRequestParameterException e) {
        return error(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", "Required parameter is missing: " + e.getParameterName());
    }
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> unsupportedMedia(org.springframework.web.HttpMediaTypeNotSupportedException e) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Content type is not supported");
    }
    @ExceptionHandler(ProfileConflictException.class) ResponseEntity<ApiError> profileConflict(ProfileConflictException e) { return error(HttpStatus.CONFLICT, e.getCode(), e.getMessage()); }
    @ExceptionHandler(ProfileNotFoundException.class) ResponseEntity<ApiError> profileNotFound(ProfileNotFoundException e) { return error(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", e.getMessage()); }
    @ExceptionHandler(OnboardingException.class) ResponseEntity<ApiError> onboarding(OnboardingException e) { return error(e.getStatus(), e.getCode(), e.getMessage()); }
    @ExceptionHandler(InternshipDetailsException.class) ResponseEntity<ApiError> internshipDetails(InternshipDetailsException e) { return error(e.getStatus(), e.getCode(), e.getMessage()); }
    @ExceptionHandler(edu.institution.ims.editing.EditPermissionException.class) ResponseEntity<ApiError> editPermission(edu.institution.ims.editing.EditPermissionException e) { return error(e.getStatus(), e.getCode(), e.getMessage()); }
    @ExceptionHandler(MonthlyProgressException.class) ResponseEntity<ApiError> monthlyProgress(MonthlyProgressException e) { return error(e.getStatus(), e.getCode(), e.getMessage()); }
    @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<ApiError> oversized() { return error(HttpStatus.PAYLOAD_TOO_LARGE, "DOCUMENT_TOO_LARGE", "Upload exceeds the configured file or total request size limit"); }
    @ExceptionHandler(HttpMessageNotReadableException.class) ResponseEntity<ApiError> unreadable() { return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request contains an invalid value"); }
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class) ResponseEntity<ApiError> invalidParameter() { return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request contains an invalid parameter value"); }
    @ExceptionHandler(MissingServletRequestPartException.class) ResponseEntity<ApiError> missingPart(MissingServletRequestPartException e) { return error(HttpStatus.BAD_REQUEST, "MISSING_REQUEST_PART", "Required upload field is missing: " + e.getRequestPartName()); }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class) ResponseEntity<ApiError> methodNotAllowed() { return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "This operation is not supported"); }
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<ApiError> invalidArgument(IllegalArgumentException e) { return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage()); }
    @ExceptionHandler(NoResourceFoundException.class) ResponseEntity<ApiError> missingResource() { return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource was not found"); }
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
        var fields = e.getBindingResult().getFieldErrors().stream().collect(Collectors.toMap(x -> x.getField(), x -> x.getDefaultMessage() == null ? "Invalid value" : x.getDefaultMessage(), (a,b) -> a));
        return ResponseEntity.badRequest().body(new ApiError(Instant.now(), 400, "VALIDATION_FAILED", "Request validation failed", fields));
    }
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("Unhandled exception caught by GlobalExceptionHandler: ", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred while processing your request. Please try again.");
    }
    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) { return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), code, message, null)); }
}
