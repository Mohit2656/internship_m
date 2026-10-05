package edu.institution.ims.professionalprofile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.util.*;
import java.util.zip.ZipInputStream;

@Component
public class ResumeFileValidator {
    static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final Map<String, String> TYPES = Map.of("pdf", "application/pdf", "docx", DOCX);
    private final long maxBytes;

    public ResumeFileValidator(@Value("${app.file-storage.resume-max-file-size-bytes}") long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public ValidatedResume validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw bad("EMPTY_RESUME", "Choose a non-empty resume to upload");
        if (file.getSize() > maxBytes) throw new ResumeException(HttpStatus.PAYLOAD_TOO_LARGE,
                "RESUME_TOO_LARGE", "Resumes must be 5 MB or smaller");
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").trim();
        if (name.isBlank() || name.length() > 255 || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0
                || name.chars().anyMatch(Character::isISOControl)) {
            throw bad("INVALID_RESUME_FILENAME", "Use a valid resume filename without path characters");
        }
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        String expectedType = TYPES.get(extension);
        if (expectedType == null) throw bad("UNSUPPORTED_RESUME", "Only PDF and DOCX resumes are allowed");
        String declaredType = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);
        if (!expectedType.equals(declaredType)) {
            throw bad("RESUME_TYPE_MISMATCH", "The filename extension and declared content type do not match");
        }
        try {
            boolean valid = "pdf".equals(extension) ? isPdf(file.getInputStream()) : isDocx(file.getInputStream());
            if (!valid) throw bad("RESUME_TYPE_MISMATCH", "The resume content does not match its file type");
        } catch (IOException e) {
            throw bad("RESUME_READ_FAILED", "The uploaded resume could not be read");
        }
        return new ValidatedResume(name, extension, expectedType);
    }

    private static boolean isPdf(InputStream input) throws IOException {
        byte[] signature = input.readNBytes(5);
        return signature.length == 5 && signature[0] == '%' && signature[1] == 'P'
                && signature[2] == 'D' && signature[3] == 'F' && signature[4] == '-';
    }

    private static boolean isDocx(InputStream input) throws IOException {
        boolean contentTypes = false;
        boolean document = false;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null && entries++ < 1024) {
                if ("[Content_Types].xml".equals(entry.getName())) contentTypes = true;
                if ("word/document.xml".equals(entry.getName())) document = true;
                if (contentTypes && document) return true;
            }
        }
        return false;
    }

    private static ResumeException bad(String code, String message) {
        return new ResumeException(HttpStatus.BAD_REQUEST, code, message);
    }
}
