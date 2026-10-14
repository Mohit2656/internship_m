package edu.institution.ims.professionalprofile;

import edu.institution.ims.internship.storage.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@Service
public class ProfessionalProfileService {
    private final StudentProfessionalProfileRepository profiles;
    private final StudentResumeRepository resumes;
    private final UserRepository users;
    private final FileStorageService storage;
    private final ResumeFileValidator validator;
    private final StudentSkillRepository skills;

    public ProfessionalProfileService(StudentProfessionalProfileRepository profiles, StudentResumeRepository resumes,
            UserRepository users, FileStorageService storage, ResumeFileValidator validator, StudentSkillRepository skills) {
        this.profiles = profiles;
        this.resumes = resumes;
        this.users = users;
        this.storage = storage;
        this.validator = validator;
        this.skills = skills;
    }

    @Transactional(readOnly = true)
    public ProfessionalProfileResponse getOwn(UserPrincipal principal) {
        return profiles.findByStudentId(principal.id()).map(this::response)
                .orElseGet(ProfessionalProfileResponse::empty);
    }

    @Transactional
    public ProfessionalProfileResponse updateOwn(UserPrincipal principal, ProfessionalProfileRequest request) {
        StudentProfessionalProfile profile = profileForUpdateOrCreate(principal.id());
        profile.update(nullable(request.headline()), nullable(request.careerInterests()));
        return response(profiles.saveAndFlush(profile));
    }

    @Transactional
    public ProfessionalProfileResponse uploadOwn(UserPrincipal principal, MultipartFile file) {
        ValidatedResume valid = validator.validate(file);
        StudentProfessionalProfile profile = profileForUpdateOrCreate(principal.id());
        String newKey;
        try {
            newKey = storage.store(file.getInputStream(), valid.extension());
        } catch (IOException e) {
            throw new ResumeException(HttpStatus.BAD_REQUEST, "RESUME_READ_FAILED", "The uploaded resume could not be read");
        }

        StudentResume previous = resumes.findByProfessionalProfileIdAndActiveTrue(profile.getId()).orElse(null);
        StudentResume resume = new StudentResume(profile);
        resume.activate(valid.originalFilename(), newKey, valid.contentType(), file.getSize());
        try {
            if (previous != null) {
                previous.deactivate();
                resumes.saveAndFlush(previous);
            }
            resumes.saveAndFlush(resume);
        } catch (RuntimeException e) {
            storage.delete(newKey);
            throw e;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) storage.delete(newKey);
            }
        });
        return response(profile);
    }

    @Transactional(readOnly = true)
    public ResumeResponse getOwnResume(UserPrincipal principal) {
        return resumeForStudent(principal.id()).map(ProfessionalProfileService::resumeResponse)
                .orElseThrow(() -> notFound("RESUME_NOT_FOUND", "No active resume was found"));
    }

    @Transactional(readOnly = true)
    public ResumeDownload downloadOwn(UserPrincipal principal) {
        return download(resumeForStudent(principal.id())
                .orElseThrow(() -> notFound("RESUME_NOT_FOUND", "No active resume was found")));
    }

    @Transactional(readOnly = true)
    public ProfessionalProfileResponse getForAdmin(Long studentId) {
        requireStudent(studentId);
        return profiles.findByStudentId(studentId).map(this::response)
                .orElseGet(ProfessionalProfileResponse::empty);
    }

    @Transactional(readOnly = true)
    public ResumeDownload downloadForAdmin(Long studentId) {
        requireStudent(studentId);
        return download(resumeForStudent(studentId)
                .orElseThrow(() -> notFound("RESUME_NOT_FOUND", "No active resume was found")));
    }

    private StudentProfessionalProfile profileForUpdateOrCreate(Long studentId) {
        return profiles.findByStudentIdForUpdate(studentId).orElseGet(() ->
                profiles.saveAndFlush(new StudentProfessionalProfile(requireStudent(studentId))));
    }

    private User requireStudent(Long studentId) {
        return users.findById(studentId).filter(user -> user.getRole() == Role.STUDENT)
                .orElseThrow(() -> notFound("STUDENT_NOT_FOUND", "Student account was not found"));
    }

    private java.util.Optional<StudentResume> resumeForStudent(Long studentId) {
        return profiles.findByStudentId(studentId)
                .flatMap(profile -> resumes.findByProfessionalProfileIdAndActiveTrue(profile.getId()));
    }

    private ProfessionalProfileResponse response(StudentProfessionalProfile profile) {
        ResumeResponse resume = resumes.findByProfessionalProfileIdAndActiveTrue(profile.getId())
                .map(ProfessionalProfileService::resumeResponse).orElse(null);
        return new ProfessionalProfileResponse(profile.getId(), profile.getHeadline(), profile.getCareerInterests(),
                resume, profile.getCreatedAt(), profile.getUpdatedAt(), skills.lastStudentUpdateByProfileId(profile.getId()));
    }

    private ResumeDownload download(StudentResume resume) {
        StoredFile file = storage.load(resume.getStorageKey());
        return new ResumeDownload(file.resource(), resume.getOriginalFilename(), resume.getContentType(), file.size());
    }

    private static ResumeResponse resumeResponse(StudentResume resume) {
        return new ResumeResponse(resume.getId(), resume.getOriginalFilename(), resume.getContentType(), resume.getFileSize(),
                resume.getUploadedAt(), resume.isActive(), resume.getParsingStatus());
    }

    private static String nullable(String value) {
        if (value == null) return null;
        String clean = value.trim();
        return clean.isBlank() ? null : clean;
    }

    private static ResumeException notFound(String code, String message) {
        return new ResumeException(HttpStatus.NOT_FOUND, code, message);
    }
}
