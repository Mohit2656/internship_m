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
    @org.springframework.beans.factory.annotation.Autowired private MonthlyProgressReviewEventRepository reviewEvents;
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired private UserRepository users;
    private final FileStorageService storage;
    private final ProgressPhotoValidator photos;
    private final MonthlyProgressPdfService pdfs;
    private final ProgressReportPdfValidator reportPdfs;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired private edu.institution.ims.notification.NotificationService notifications;
    @org.springframework.beans.factory.annotation.Autowired private edu.institution.ims.notification.MentorNotificationService mentorNotifications;

    public MonthlyProgressService(MonthlyProgressReportRepository reports, InternshipDetailsRepository internships,
            MentorStudentAssignmentRepository assignments, FileStorageService storage, ProgressPhotoValidator photos,
            MonthlyProgressPdfService pdfs, ProgressReportPdfValidator reportPdfs, Clock clock) {
        this.reports = reports;
        this.internships = internships;
        this.assignments = assignments;
        this.storage = storage;
        this.photos = photos;
        this.pdfs = pdfs;
        this.reportPdfs = reportPdfs;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MonthlyProgressOverviewResponse ownOverview(UserPrincipal principal) {
        return ownOverview(principal, null);
    }

    @Transactional(readOnly = true)
    public MonthlyProgressOverviewResponse ownOverview(UserPrincipal principal, Long internshipId) {
        InternshipDetails internship = eligibleInternship(principal.id(), internshipId);
        Map<Integer, MonthlyProgressReport> submitted = new HashMap<>();
        reports.findAllByStudentInternshipIdOrderByReportMonthNumberAsc(internship.getId())
                .forEach(report -> submitted.put(report.getReportMonthNumber(), report));
        LocalDate today = LocalDate.now(clock);
        List<MonthlyProgressPeriodResponse> periods = periods(internship).stream().map(period -> {
            MonthlyProgressReport report = submitted.get(period.monthNumber());
            String status = report != null ? report.getStatus().name() : today.isBefore(period.start()) ? "UPCOMING" : "AVAILABLE";
            return new MonthlyProgressPeriodResponse(period.monthNumber(), period.start(), period.end(), status,
                    report == null ? null : response(report));
        }).toList();
        return new MonthlyProgressOverviewResponse(internship.getId(), internship.getCompanyName(),
                internship.getInternshipMode().getLabel(), internship.getStartDate(), internship.getEndDate(), periods, photos.maxBytes());
    }

    @Transactional
    public MonthlyProgressReportResponse submit(UserPrincipal principal, int monthNumber, String description, MultipartFile photo) {
        return submit(principal, null, monthNumber, description, photo);
    }

    @Transactional
    public MonthlyProgressReportResponse submit(UserPrincipal principal, Long internshipId, int monthNumber, String description, MultipartFile photo) {
        return submit(principal, internshipId, monthNumber, description, photo, null);
    }
    @Transactional
    public MonthlyProgressReportResponse submit(UserPrincipal principal, Long internshipId, int monthNumber, String description, MultipartFile photo, MultipartFile reportPdf) {
        InternshipDetails internship = eligibleInternship(principal.id(), internshipId);
        Period period = period(internship, monthNumber);
        if (LocalDate.now(clock).isBefore(period.start()))
            throw error(HttpStatus.CONFLICT, "REPORT_MONTH_UPCOMING", "This internship month has not started yet");
        String work = cleanDescription(description);
        if (reports.existsByStudentInternshipIdAndReportMonthNumber(internship.getId(), monthNumber)) throw duplicate();
        ValidatedProgressPhoto image = photos.validate(photo);
        String storageKey = store(image);
        MonthlyProgressReport report = new MonthlyProgressReport(internship, monthNumber, period.start(), period.end(), work,
                storageKey, image.originalName(), image.mimeType(), image.size(), null, null, null, clock.instant());
        report.applyImageIntegrity(image);
        if (reportPdf != null && !reportPdf.isEmpty()) attachPdf(report, reportPdf);
        try { reports.saveAndFlush(report); }
        catch (DataIntegrityViolationException exception) { throw duplicate(); }
        submissionEvent(report);
        return response(report);
    }

    @Transactional
    public MonthlyProgressReportResponse resubmit(UserPrincipal principal, Long id, int revision, String description, MultipartFile photo) {
        return resubmit(principal, id, revision, description, photo, null);
    }
    @Transactional
    public MonthlyProgressReportResponse resubmit(UserPrincipal principal, Long id, int revision, String description, MultipartFile photo, MultipartFile reportPdf) {
        MonthlyProgressReport report = reports.findForReviewById(id).orElseThrow(MonthlyProgressService::notFound);
        if (!studentId(report).equals(principal.id())) throw notFound();
        eligibleInternship(principal.id(),report.getStudentInternship().getId());
        requireRevision(report,revision);
        if (report.getStatus()!=MonthlyProgressStatus.REJECTED)
            throw error(HttpStatus.CONFLICT,"REPORT_NOT_REJECTED","Only a rejected report may be corrected and resubmitted");
        String work=cleanDescription(description);
        ValidatedProgressPhoto image=photos.validate(photo);
        String key=store(image);
        report.resubmit(work,key,image,clock.instant());
        if (reportPdf != null && !reportPdf.isEmpty()) attachPdf(report, reportPdf);
        reports.saveAndFlush(report);
        submissionEvent(report);
        return response(report);
    }

    @Transactional
    public MonthlyProgressReportResponse review(UserPrincipal principal, Long id, int revision, boolean approved, String reason) {
        if (principal==null || !principal.active() || !"MENTOR".equals(principal.role()))
            throw error(HttpStatus.FORBIDDEN,"MENTOR_REQUIRED","Only the assigned faculty mentor may review a report");
        // Shared mentor -> active student -> assignment -> report, matching other review workflows.
        var owners=jdbc.queryForList("select o.student_user_id from monthly_progress_reports r join internship_details i on i.id=r.student_internship_id join internship_onboarding o on o.id=i.onboarding_id where r.id=? and r.deleted_at is null",Long.class,id);
        if (owners.isEmpty()) throw notFound();
        users.findByIdForWorkflowRead(principal.id()).filter(u->u.getRole()==Role.MENTOR && u.isActive()).orElseThrow(MonthlyProgressService::notFound);
        if(!WorkflowUserLocks.student(jdbc,owners.get(0),true)) throw notFound();
        var scoped=jdbc.queryForList("select id from mentor_student_assignments where student_user_id=? and mentor_user_id=? and active=true for update",Long.class,owners.get(0),principal.id());
        if (scoped.isEmpty()) throw notFound();
        MonthlyProgressReport report=reports.findForReviewById(id).orElseThrow(MonthlyProgressService::notFound);
        requireRevision(report,revision);
        if (report.getStatus()!=MonthlyProgressStatus.PENDING_MENTOR_REVIEW)
            throw error(HttpStatus.CONFLICT,"REPORT_ALREADY_REVIEWED","This report has already been reviewed");
        String clean=approved?null:reason==null?"":reason.strip();
        if (!approved && (clean.isBlank() || clean.length()>1000))
            throw error(HttpStatus.BAD_REQUEST,"REJECTION_REASON_REQUIRED","Provide a rejection reason of 1 to 1000 characters");
        User reviewer=users.findById(principal.id()).orElseThrow(MonthlyProgressService::notFound);
        report.decide(reviewer,approved,clean,clock.instant());
        reports.saveAndFlush(report);
        reviewEvents.saveAndFlush(new MonthlyProgressReviewEvent(report,reviewer,approved?"APPROVED":"REJECTED",clean,clock.instant()));
        if (!approved) notifications.reportRejected(report, reviewer);
        else notifications.create(studentId(report),"report-approved:"+report.getId()+":"+report.getRevision(),"REPORT_APPROVED",
                "Monthly Progress Report - Month "+report.getReportMonthNumber()+" approved",
                "Your faculty mentor approved this monthly progress report.",null,principal.email(),
                "/student/progress-reports?tab=monthly&internshipId="+report.getStudentInternship().getId()+"#report-"+report.getId(),report.getReviewedAt());
        return response(report);
    }

    private static void requireRevision(MonthlyProgressReport report,int revision) {
        if (revision!=report.getRevision()) throw error(HttpStatus.CONFLICT,"REPORT_REVISION_CHANGED","The report changed; refresh it before continuing");
    }
    private void submissionEvent(MonthlyProgressReport report) {
        reviewEvents.saveAndFlush(new MonthlyProgressReviewEvent(report,report.getStudentInternship().getOnboarding().getStudent(),"SUBMITTED",null,report.getSubmittedAt()));
        mentorNotifications.workflowSubmitted(studentId(report),"monthly-submitted:"+report.getId()+":"+report.getRevision(),"MONTHLY_REPORT_SUBMITTED",
                "Monthly progress requires review",Objects.toString(response(report).studentName(),"Student")+" submitted month "+report.getReportMonthNumber()
                        +" at "+report.getStudentInternship().getCompanyName()+".",
                "/mentor/progress?tab=monthly&internshipId="+report.getStudentInternship().getId()+"#report-"+report.getId(),report.getSubmittedAt());
    }
    private String store(ValidatedProgressPhoto image) {
        String key;
        try (InputStream input=new ByteArrayInputStream(image.bytes())) { key=storage.store(input,image.extension()); }
        catch(IOException exception) { throw error(HttpStatus.BAD_REQUEST,"PHOTO_READ_FAILED","The uploaded photograph could not be read"); }
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCompletion(int status) { if (status!=STATUS_COMMITTED) storage.delete(key); }
            });
        }
        return key;
    }
    private void attachPdf(MonthlyProgressReport report, MultipartFile multipart) {
        ProgressReportPdfValidator.ValidatedPdf pdf=reportPdfs.validate(multipart);
        String previous=report.getReportPdfStorageKey();
        String key;
        try (InputStream input=new ByteArrayInputStream(pdf.bytes())) { key=storage.store(input,"pdf"); }
        catch(IOException exception) { throw error(HttpStatus.BAD_REQUEST,"REPORT_PDF_READ_FAILED","The uploaded PDF could not be read"); }
        report.attachReportPdf(key,pdf.originalName(),pdf.size());
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if(status!=STATUS_COMMITTED) storage.delete(key); else if(previous!=null&&!previous.equals(key)) storage.delete(previous);
                }
            });
        }
    }

    @Transactional(readOnly = true) public MonthlyProgressReportResponse ownReport(UserPrincipal principal, Long id) {
        return response(require(id, report -> studentId(report).equals(principal.id())));
    }
    @Transactional(readOnly = true) public List<MonthlyProgressReportResponse> mentorReports(UserPrincipal principal) {
        List<Long> studentIds = assignments.findAllByMentorIdAndActiveTrueOrderByStudentEmailAsc(principal.id())
                .stream().filter(a->a.getStudent().isActive()).map(assignment -> assignment.getStudent().getId()).toList();
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
        return photo(require(id, report -> assignedTo(principal.id(), studentId(report)) || isReviewer(principal.id(), report)));
    }
    @Transactional(readOnly = true) public ProgressFileDownload adminPhoto(Long id) { return photo(require(id, report -> true)); }
    @Transactional(readOnly = true) public ProgressFileDownload ownPdf(UserPrincipal principal, Long id) {
        return pdf(require(id, report -> studentId(report).equals(principal.id())));
    }
    @Transactional(readOnly = true) public ProgressFileDownload mentorPdf(UserPrincipal principal, Long id) {
        return pdf(require(id, report -> assignedTo(principal.id(), studentId(report)) || isReviewer(principal.id(), report)));
    }
    @Transactional(readOnly = true) public ProgressFileDownload adminPdf(Long id) { return pdf(require(id, report -> true)); }
    @Transactional(readOnly = true) public ProgressFileDownload ownAttachment(UserPrincipal principal, Long id) {
        return attachment(require(id, report -> studentId(report).equals(principal.id())));
    }
    @Transactional(readOnly = true) public ProgressFileDownload mentorAttachment(UserPrincipal principal, Long id) {
        return attachment(require(id, report -> assignedTo(principal.id(), studentId(report)) || isReviewer(principal.id(), report)));
    }
    @Transactional(readOnly = true) public ProgressFileDownload adminAttachment(Long id) {
        return attachment(require(id, report -> true));
    }
    private boolean isReviewer(Long mentorId, MonthlyProgressReport report) {
        return report.getReviewer() != null && report.getReviewer().getId().equals(mentorId);
    }
    private ProgressFileDownload attachment(MonthlyProgressReport report) {
        if(report.getReportPdfStorageKey()==null) throw notFound();
        StoredFile stored=storage.load(report.getReportPdfStorageKey());
        return ProgressFileDownload.resource(stored.resource(),report.getReportPdfOriginalName(),"application/pdf",stored.size());
    }

    private InternshipDetails eligibleInternship(Long studentId) {
        return eligibleInternship(studentId, null);
    }

    private InternshipDetails eligibleInternship(Long studentId, Long internshipId) {
        InternshipDetails internship = (internshipId != null
                ? internships.findByIdAndOnboardingStudentId(internshipId, studentId)
                : internships.findByOnboardingStudentId(studentId))
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
        byte[] photoBytes = null;
        if (report.getPhotoStorageKey() != null) {
            try {
                StoredFile photo = storage.load(report.getPhotoStorageKey());
                try (InputStream input = photo.resource().getInputStream()) {
                    photoBytes = input.readAllBytes();
                }
            } catch (Exception ignored) {}
        }
        User student = report.getStudentInternship().getOnboarding().getStudent();
        boolean hasTwoInternships = jdbc.queryForObject("select count(*) from second_internship_requests where student_user_id=? and status='APPROVED'", Integer.class, student.getId()) > 0;
        String facultyMentor = jdbc.query(
                "select coalesce(mp.name, mu.email) from mentor_student_assignments a join users mu on mu.id=a.mentor_user_id left join mentor_profiles mp on mp.user_id=mu.id where a.student_user_id=? and a.active=true",
                (rs, rowNum) -> rs.getString(1), student.getId()).stream().findFirst().orElse(report.getReviewer() != null ? report.getReviewer().getEmail() : "Not assigned");

        byte[] result = pdfs.generate(report, photoBytes, hasTwoInternships, facultyMentor);
        String uid = response(report).uid();
        String safeUid = (uid == null ? "Student" : uid).replaceAll("[^A-Za-z0-9_-]", "_");
        return ProgressFileDownload.bytes(result, "Monthly_Progress_" + safeUid + "_Internship_"
                + report.getStudentInternship().getOnboarding().getInternshipNumber() + "_Month_"
                + report.getReportMonthNumber() + ".pdf", "application/pdf");
    }
    private static String cleanDescription(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isBlank()) throw error(HttpStatus.BAD_REQUEST, "WORK_DESCRIPTION_REQUIRED", "Describe the work performed during this month");
        if (clean.length() > 3000) throw error(HttpStatus.BAD_REQUEST, "WORK_DESCRIPTION_TOO_LONG", "Work description must be 3000 characters or fewer");
        return clean;
    }
    private static List<Period> periods(InternshipDetails internship) {
        List<Period> result = new ArrayList<>();
        LocalDate effectiveEnd = internship.isEarlyTerminated() && internship.getTerminatedAt() != null
                ? internship.getTerminatedAt().atZone(ZoneId.of("Asia/Kolkata")).toLocalDate()
                : internship.getEndDate();
        LocalDate start = internship.getStartDate();
        for (int month = 1; ; month++) {
            LocalDate periodStart = start.plusMonths(month - 1L);
            if (periodStart.isAfter(effectiveEnd)) break;
            LocalDate periodEnd = start.plusMonths(month).minusDays(1);
            if (periodEnd.isAfter(effectiveEnd)) periodEnd = effectiveEnd;
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
        Integer semester = null;
        if (profile != null) {
            semester = profile.getSemester();
        } else if (account != null) {
            semester = account.getSemester();
        }
        InternshipDetails internship = report.getStudentInternship();
        return new MonthlyProgressReportResponse(report.getId(), student.getId(), name, uid, branch, semester,
                internship.getId(), internship.getOnboarding().getInternshipNumber(), internship.getCompanyName(), internship.getInternshipMode().getLabel(),
                report.getReportMonthNumber(), report.getPeriodStart(), report.getPeriodEnd(), report.getWorkDescription(),
                report.getPhotoOriginalName(), report.getPhotoMimeType(), report.getPhotoSize(),
                report.getReportPdfOriginalName(), report.getReportPdfSize(), report.getSubmittedAt(),
                report.getImageWidth(), report.getImageHeight(), report.getImageSha256(), report.getStatus(), report.getRevision(),
                report.getReviewer()==null?null:report.getReviewer().getId(),
                report.getReviewer()==null?null:report.getReviewer().getEmail(), report.getReviewedAt(), report.getRejectionReason());
    }

    private static MonthlyProgressException duplicate() { return error(HttpStatus.CONFLICT, "MONTHLY_REPORT_ALREADY_SUBMITTED", "A report has already been submitted for this internship month"); }
    private static MonthlyProgressException invalidMonth() { return error(HttpStatus.BAD_REQUEST, "INVALID_REPORT_MONTH", "The report month is outside the registered internship period"); }
    private static MonthlyProgressException notFound() { return error(HttpStatus.NOT_FOUND, "MONTHLY_REPORT_NOT_FOUND", "Monthly progress report was not found"); }
    private static MonthlyProgressException error(HttpStatus status, String code, String message) { return new MonthlyProgressException(status, code, message); }
    private record Period(int monthNumber, LocalDate start, LocalDate end) {}
}
