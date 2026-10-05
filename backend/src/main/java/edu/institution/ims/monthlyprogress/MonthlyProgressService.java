package edu.institution.ims.monthlyprogress;

import edu.institution.ims.internship.*;
import edu.institution.ims.internship.storage.*;
import edu.institution.ims.mentor.MentorStudentAssignmentRepository;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.studentprofile.StudentProfile;
import edu.institution.ims.user.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.function.Predicate;

@Service
public class MonthlyProgressService {
    private final MonthlyProgressReportRepository reports;
    private final InternshipDetailsRepository internships;
    private final MentorStudentAssignmentRepository assignments;
    private final FileStorageService storage;
    private final ProgressPhotoValidator photos;
    private final MonthlyProgressPdfService pdfs;
    private final Clock clock;

    public MonthlyProgressService(MonthlyProgressReportRepository reports, InternshipDetailsRepository internships,
            MentorStudentAssignmentRepository assignments, FileStorageService storage, ProgressPhotoValidator photos,
            MonthlyProgressPdfService pdfs, Clock clock) {
        this.reports = reports;
        this.internships = internships;
        this.assignments = assignments;
        this.storage = storage;
        this.photos = photos;
        this.pdfs = pdfs;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MonthlyProgressOverviewResponse ownOverview(UserPrincipal principal) {
        InternshipDetails internship = eligibleInternship(principal.id());
        Map<Integer, MonthlyProgressReport> submitted = new HashMap<>();
        reports.findAllByStudentInternshipIdOrderByReportMonthNumberAsc(internship.getId())
                .forEach(report -> submitted.put(report.getReportMonthNumber(), report));
        LocalDate today = LocalDate.now(clock);
        List<MonthlyProgressPeriodResponse> periods = periods(internship).stream().map(period -> {
            MonthlyProgressReport report = submitted.get(period.monthNumber());
            String status = report != null ? "SUBMITTED" : today.isBefore(period.start()) ? "UPCOMING" : "AVAILABLE";
            return new MonthlyProgressPeriodResponse(period.monthNumber(), period.start(), period.end(), status,
                    report == null ? null : response(report));
        }).toList();
        return new MonthlyProgressOverviewResponse(internship.getId(), internship.getCompanyName(),
                internship.getInternshipMode().getLabel(), internship.getStartDate(), internship.getEndDate(), periods);
    }

    @Transactional
    public MonthlyProgressReportResponse submit(UserPrincipal principal, int monthNumber, String description, MultipartFile photo) {
        InternshipDetails internship = eligibleInternship(principal.id());
        Period period = period(internship, monthNumber);
        if (LocalDate.now(clock).isBefore(period.start()))
            throw error(HttpStatus.CONFLICT, "REPORT_MONTH_UPCOMING", "This internship month has not started yet");
        String work = cleanDescription(description);
        if (reports.existsByStudentInternshipIdAndReportMonthNumber(internship.getId(), monthNumber)) throw duplicate();
        ValidatedProgressPhoto image = photos.validate(photo);
        String storageKey;
        try (InputStream input = photo.getInputStream()) { storageKey = storage.store(input, image.extension()); }
        catch (IOException exception) { throw error(HttpStatus.BAD_REQUEST, "PHOTO_READ_FAILED", "The uploaded photograph could not be read"); }
        try {
            MonthlyProgressReport report = new MonthlyProgressReport(internship, monthNumber, period.start(), period.end(), work,
                    storageKey, image.originalName(), image.mimeType(), image.size(), image.capturedAt(), image.latitude(), image.longitude(), clock.instant());
            return response(reports.saveAndFlush(report));
        } catch (DataIntegrityViolationException exception) {
            storage.delete(storageKey);
            throw duplicate();
        } catch (RuntimeException exception) {
            storage.delete(storageKey);
            throw exception;
        }
    }

    @Transactional(readOnly = true) public MonthlyProgressReportResponse ownReport(UserPrincipal principal, Long id) {
        return response(require(id, report -> studentId(report).equals(principal.id())));
    }
    @Transactional(readOnly = true) public List<MonthlyProgressReportResponse> mentorReports(UserPrincipal principal) {
        List<Long> studentIds = assignments.findAllByMentorIdAndActiveTrueOrderByStudentEmailAsc(principal.id())
                .stream().map(assignment -> assignment.getStudent().getId()).toList();
        if (studentIds.isEmpty()) return List.of();
        return reports.findAllByStudentInternshipOnboardingStudentIdInOrderBySubmittedAtDesc(studentIds).stream().map(MonthlyProgressService::response).toList();
    }
    @Transactional(readOnly = true) public MonthlyProgressReportResponse mentorReport(UserPrincipal principal, Long id) {
        return response(require(id, report -> assignedTo(principal.id(), studentId(report))));
    }
    @Transactional(readOnly = true) public List<MonthlyProgressReportResponse> adminReports() {
        return reports.findAll().stream().sorted(Comparator.comparing(MonthlyProgressReport::getSubmittedAt).reversed())
                .map(MonthlyProgressService::response).toList();
    }
    @Transactional(readOnly = true) public MonthlyProgressReportResponse adminReport(Long id) { return response(require(id, report -> true)); }

    @Transactional(readOnly = true) public ProgressFileDownload ownPhoto(UserPrincipal principal, Long id) {
        return photo(require(id, report -> studentId(report).equals(principal.id())));
    }
    @Transactional(readOnly = true) public ProgressFileDownload mentorPhoto(UserPrincipal principal, Long id) {
        return photo(require(id, report -> assignedTo(principal.id(), studentId(report))));
    }
    @Transactional(readOnly = true) public ProgressFileDownload adminPhoto(Long id) { return photo(require(id, report -> true)); }
    @Transactional(readOnly = true) public ProgressFileDownload ownPdf(UserPrincipal principal, Long id) {
        return pdf(require(id, report -> studentId(report).equals(principal.id())));
    }
    @Transactional(readOnly = true) public ProgressFileDownload mentorPdf(UserPrincipal principal, Long id) {
        return pdf(require(id, report -> assignedTo(principal.id(), studentId(report))));
    }
    @Transactional(readOnly = true) public ProgressFileDownload adminPdf(Long id) { return pdf(require(id, report -> true)); }

    private InternshipDetails eligibleInternship(Long studentId) {
        InternshipDetails internship = internships.findByOnboardingStudentId(studentId)
                .orElseThrow(() -> error(HttpStatus.CONFLICT, "MONTHLY_PROGRESS_NOT_ELIGIBLE", "Complete internship registration before submitting monthly progress"));
        if (internship.getOnboarding().getWorkflowStatus() != WorkflowStatus.INTERNSHIP_DETAILS_COMPLETE)
            throw error(HttpStatus.CONFLICT, "MONTHLY_PROGRESS_NOT_ELIGIBLE", "Complete internship registration before submitting monthly progress");
        return internship;
    }

    private MonthlyProgressReport require(Long id, Predicate<MonthlyProgressReport> permitted) {
        MonthlyProgressReport report = reports.findById(id).orElseThrow(MonthlyProgressService::notFound);
        if (!permitted.test(report)) throw notFound();
        return report;
    }
    private boolean assignedTo(Long mentorId, Long studentId) {
        return assignments.findByStudentIdAndActiveTrue(studentId)
                .filter(assignment -> assignment.getMentor().getId().equals(mentorId)).isPresent();
    }
    private ProgressFileDownload photo(MonthlyProgressReport report) {
        StoredFile stored = storage.load(report.getPhotoStorageKey());
        return ProgressFileDownload.resource(stored.resource(), report.getPhotoOriginalName(), report.getPhotoMimeType(), stored.size());
    }
    private ProgressFileDownload pdf(MonthlyProgressReport report) {
        StoredFile photo = storage.load(report.getPhotoStorageKey());
        try (InputStream input = photo.resource().getInputStream()) {
            byte[] result = pdfs.generate(report, input.readAllBytes());
            String uid = response(report).uid();
            String safeUid = (uid == null ? "Student" : uid).replaceAll("[^A-Za-z0-9_-]", "_");
            return ProgressFileDownload.bytes(result, "Monthly_Progress_" + safeUid + "_Month_" + report.getReportMonthNumber() + ".pdf", "application/pdf");
        } catch (IOException exception) {
            throw error(HttpStatus.INTERNAL_SERVER_ERROR, "PHOTO_READ_FAILED", "The stored photograph could not be read");
        }
    }
    private static String cleanDescription(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isBlank()) throw error(HttpStatus.BAD_REQUEST, "WORK_DESCRIPTION_REQUIRED", "Describe the work performed during this month");
        if (clean.length() > 3000) throw error(HttpStatus.BAD_REQUEST, "WORK_DESCRIPTION_TOO_LONG", "Work description must be 3000 characters or fewer");
        return clean;
    }
    private static List<Period> periods(InternshipDetails internship) {
        List<Period> result = new ArrayList<>();
        LocalDate start = internship.getStartDate();
        for (int month = 1; ; month++) {
            LocalDate periodStart = start.plusMonths(month - 1L);
            if (periodStart.isAfter(internship.getEndDate())) break;
            LocalDate periodEnd = start.plusMonths(month).minusDays(1);
            if (periodEnd.isAfter(internship.getEndDate())) periodEnd = internship.getEndDate();
            result.add(new Period(month, periodStart, periodEnd));
        }
        return result;
    }
    private static Period period(InternshipDetails internship, int monthNumber) {
        if (monthNumber < 1) throw invalidMonth();
        return periods(internship).stream().filter(value -> value.monthNumber() == monthNumber).findFirst().orElseThrow(MonthlyProgressService::invalidMonth);
    }
    private static Long studentId(MonthlyProgressReport report) { return report.getStudentInternship().getOnboarding().getStudent().getId(); }
    static MonthlyProgressReportResponse response(MonthlyProgressReport report) {
        User student = report.getStudentInternship().getOnboarding().getStudent();
        StudentProfile profile = student.getStudentProfile();
        StudentAccount account = student.getStudentAccount();
        String name = profile != null ? profile.getStudentName() : account == null ? null : account.getStudentName();
        String uid = profile != null ? profile.getUid() : account == null ? null : account.getUid();
        String branch = profile == null ? null : profile.getBranch().getLabel();
        Integer semester = profile != null ? profile.getSemester() : account == null ? null : account.getSemester();
        InternshipDetails internship = report.getStudentInternship();
        return new MonthlyProgressReportResponse(report.getId(), student.getId(), name, uid, branch, semester,
                internship.getId(), internship.getCompanyName(), internship.getInternshipMode().getLabel(),
                report.getReportMonthNumber(), report.getPeriodStart(), report.getPeriodEnd(), report.getWorkDescription(),
                report.getPhotoOriginalName(), report.getPhotoMimeType(), report.getPhotoSize(), report.getExifCapturedAt(),
                report.getExifLatitude(), report.getExifLongitude(), report.getLocationName(), report.getSubmittedAt());
    }
    private static MonthlyProgressException duplicate() { return error(HttpStatus.CONFLICT, "MONTHLY_REPORT_ALREADY_SUBMITTED", "A report has already been submitted for this internship month"); }
    private static MonthlyProgressException invalidMonth() { return error(HttpStatus.BAD_REQUEST, "INVALID_REPORT_MONTH", "The report month is outside the registered internship period"); }
    private static MonthlyProgressException notFound() { return error(HttpStatus.NOT_FOUND, "MONTHLY_REPORT_NOT_FOUND", "Monthly progress report was not found"); }
    private static MonthlyProgressException error(HttpStatus status, String code, String message) { return new MonthlyProgressException(status, code, message); }
    private record Period(int monthNumber, LocalDate start, LocalDate end) {}
}
