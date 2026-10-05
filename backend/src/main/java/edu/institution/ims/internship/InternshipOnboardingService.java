package edu.institution.ims.internship;

import edu.institution.ims.internship.dto.*;
import edu.institution.ims.internship.storage.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.mentor.MentorStudentAssignmentRepository;
import edu.institution.ims.studentprofile.StudentProfileRepository;
import edu.institution.ims.user.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InternshipOnboardingService {
    public static final int REQUIRED_DOCUMENTS = DocumentType.values().length;
    private final InternshipOnboardingRepository onboardings;
    private final InternshipDocumentRepository documents;
    private final StudentProfileRepository profiles;
    private final UserRepository users;
    private final FileStorageService storage;
    private final DocumentFileValidator validator;
    private final MentorStudentAssignmentRepository assignments;

    public InternshipOnboardingService(InternshipOnboardingRepository onboardings, InternshipDocumentRepository documents,
            StudentProfileRepository profiles, UserRepository users, FileStorageService storage, DocumentFileValidator validator,
            MentorStudentAssignmentRepository assignments) {
        this.onboardings = onboardings; this.documents = documents; this.profiles = profiles; this.users = users; this.storage = storage;
        this.validator = validator; this.assignments = assignments;
    }

    @Transactional(readOnly = true) public OnboardingResponse getOwn(UserPrincipal principal) { return getForStudent(principal.id()); }

    @Transactional public OnboardingResponse secure(UserPrincipal principal) {
        if (!profiles.existsByUserId(principal.id())) throw conflict("PROFILE_INCOMPLETE", "Complete your student profile before registering an internship");
        InternshipOnboarding onboarding = onboardings.findByStudentId(principal.id()).orElseGet(() -> {
            User student = users.findById(principal.id()).filter(u -> u.getRole() == Role.STUDENT)
                    .orElseThrow(() -> notFound("STUDENT_NOT_FOUND", "Student account was not found"));
            return onboardings.saveAndFlush(new InternshipOnboarding(student));
        });
        return response(onboarding);
    }

    @Transactional public OnboardingResponse selectSource(UserPrincipal principal, InternshipSource source) {
        InternshipOnboarding onboarding = ownOnboarding(principal.id());
        onboarding.selectSource(Objects.requireNonNull(source));
        return response(onboardings.saveAndFlush(onboarding));
    }

    @Transactional public OnboardingResponse upload(UserPrincipal principal, DocumentType type, MultipartFile file) {
        InternshipOnboarding onboarding = ownOnboarding(principal.id());
        if (onboarding.getSource() == null) throw conflict("SOURCE_REQUIRED", "Select the internship source before uploading documents");
        ValidatedFile valid = validator.validate(file);
        String newKey;
        try { newKey = storage.store(file.getInputStream(), valid.extension()); }
        catch (IOException e) { throw new OnboardingException(HttpStatus.BAD_REQUEST, "DOCUMENT_READ_FAILED", "The uploaded document could not be read"); }
        InternshipDocument document = documents.findByOnboardingIdAndDocumentType(onboarding.getId(), type)
                .orElseGet(() -> new InternshipDocument(onboarding, type));
        String oldKey = document.getStorageKey();
        document.replace(valid.originalFileName(), newKey, valid.contentType(), file.getSize());
        try { documents.saveAndFlush(document); }
        catch (RuntimeException e) { storage.delete(newKey); throw e; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { if (oldKey != null && !oldKey.equals(newKey)) storage.delete(oldKey); }
            @Override public void afterCompletion(int status) { if (status != STATUS_COMMITTED) storage.delete(newKey); }
        });
        if (documents.countByOnboardingId(onboarding.getId()) == REQUIRED_DOCUMENTS) onboarding.markDocumentsComplete();
        return response(onboarding);
    }

    @Transactional public OnboardingResponse confirmDiary(UserPrincipal principal) {
        InternshipOnboarding onboarding = ownOnboarding(principal.id());
        if (documents.countByOnboardingId(onboarding.getId()) != REQUIRED_DOCUMENTS)
            throw conflict("DOCUMENTS_INCOMPLETE", "All four required documents must be uploaded before confirming the internship diary");
        onboarding.markDocumentsComplete();
        onboarding.confirmDiary();
        return response(onboarding);
    }

    @Transactional(readOnly = true) public DocumentDownload downloadOwn(UserPrincipal principal, Long documentId) {
        InternshipDocument document = documents.findById(documentId)
                .filter(d -> d.getOnboarding().getStudent().getId().equals(principal.id()))
                .orElseThrow(() -> notFound("DOCUMENT_NOT_FOUND", "Document was not found"));
        return download(document);
    }

    @Transactional(readOnly = true) public DocumentDownload downloadForAdmin(Long studentId, Long documentId) {
        InternshipDocument document = documents.findById(documentId)
                .filter(d -> d.getOnboarding().getStudent().getId().equals(studentId))
                .orElseThrow(() -> notFound("DOCUMENT_NOT_FOUND", "Document was not found"));
        return download(document);
    }

    @Transactional(readOnly = true) public OnboardingResponse getForMentor(UserPrincipal principal, Long studentId) {
        requireActiveAssignment(principal.id(), studentId);
        return getForStudent(studentId);
    }

    @Transactional(readOnly = true) public DocumentDownload downloadForMentor(UserPrincipal principal, Long studentId, Long documentId) {
        requireActiveAssignment(principal.id(), studentId);
        InternshipDocument document = documents.findById(documentId)
                .filter(d -> d.getOnboarding().getStudent().getId().equals(studentId))
                .orElseThrow(() -> notFound("DOCUMENT_NOT_FOUND", "Document was not found"));
        return download(document);
    }

    @Transactional(readOnly = true) public OnboardingResponse getForStudent(Long studentId) {
        return onboardings.findByStudentId(studentId).map(this::response).orElseGet(InternshipOnboardingService::notSecuredResponse);
    }

    public OnboardingResponse response(InternshipOnboarding onboarding) {
        Map<DocumentType, InternshipDocument> uploaded = documents.findAllByOnboardingIdOrderByDocumentType(onboarding.getId()).stream()
                .collect(Collectors.toMap(InternshipDocument::getDocumentType, Function.identity()));
        List<DocumentResponse> documentResponses = Arrays.stream(DocumentType.values()).map(type -> documentResponse(type, uploaded.get(type))).toList();
        return new OnboardingResponse(onboarding.getId(), onboarding.getWorkflowStatus().name(),
                onboarding.getSource() == null ? null : onboarding.getSource().name(), onboarding.getSource() == null ? null : onboarding.getSource().getLabel(),
                uploaded.size(), REQUIRED_DOCUMENTS, documentResponses, onboarding.isDiaryIssued(), onboarding.getSecuredAt(),
                onboarding.getDocumentsCompletedAt(), onboarding.getDiaryIssuedAt(), onboarding.getCreatedAt(), onboarding.getUpdatedAt());
    }

    public static OnboardingResponse notSecuredResponse() {
        List<DocumentResponse> placeholders = Arrays.stream(DocumentType.values()).map(type -> documentResponse(type, null)).toList();
        return new OnboardingResponse(null, WorkflowStatus.NOT_SECURED.name(), null, null, 0, REQUIRED_DOCUMENTS, placeholders, false,
                null, null, null, null, null);
    }

    private InternshipOnboarding ownOnboarding(Long studentId) {
        return onboardings.findByStudentId(studentId).orElseThrow(() -> conflict("ONBOARDING_NOT_STARTED", "Mark the internship as secured before continuing"));
    }
    private void requireActiveAssignment(Long mentorId, Long studentId) {
        assignments.findByStudentIdAndActiveTrue(studentId)
                .filter(assignment -> assignment.getMentor().getId().equals(mentorId))
                .orElseThrow(() -> notFound("STUDENT_NOT_ASSIGNED", "Student was not found in your active assignments"));
    }
    private DocumentDownload download(InternshipDocument document) {
        StoredFile file = storage.load(document.getStorageKey());
        return new DocumentDownload(file.resource(), document.getOriginalFileName(), document.getContentType(), file.size());
    }
    private static DocumentResponse documentResponse(DocumentType type, InternshipDocument document) {
        return new DocumentResponse(document == null ? null : document.getId(), type.name(), type.getLabel(), document != null,
                document == null ? null : document.getOriginalFileName(), document == null ? null : document.getContentType(),
                document == null ? null : document.getFileSize(), document == null ? null : document.getUploadedAt());
    }
    private static OnboardingException conflict(String code, String message) { return new OnboardingException(HttpStatus.CONFLICT, code, message); }
    private static OnboardingException notFound(String code, String message) { return new OnboardingException(HttpStatus.NOT_FOUND, code, message); }
}
