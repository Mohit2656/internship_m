package edu.institution.ims.internship.storage;

import edu.institution.ims.internship.OnboardingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.Paths;
import java.util.*;

@Component
public class DocumentFileValidator {
    private static final Map<String, String> TYPES = Map.of(
            "pdf", "application/pdf", "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png");
    private final long maxBytes;
    public DocumentFileValidator(@Value("${app.file-storage.max-file-size-bytes}") long maxBytes) { this.maxBytes = maxBytes; }

    public ValidatedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw bad("EMPTY_DOCUMENT", "Choose a non-empty document to upload");
        if (file.getSize() > maxBytes) throw new OnboardingException(HttpStatus.PAYLOAD_TOO_LARGE, "DOCUMENT_TOO_LARGE", "Documents must be 10 MB or smaller");
        String suppliedName = Optional.ofNullable(file.getOriginalFilename()).orElse("").trim();
        var fileNamePath = Paths.get(suppliedName.replace('\\', '/')).getFileName();
        String safeName = (fileNamePath == null ? "" : fileNamePath.toString()).replaceAll("[\\p{Cntrl}]", "");
        if (safeName.length() > 255) throw bad("INVALID_FILENAME", "The document filename must be 255 characters or fewer");
        int dot = safeName.lastIndexOf('.');
        String extension = dot < 0 ? "" : safeName.substring(dot + 1).toLowerCase(Locale.ROOT);
        String expectedType = TYPES.get(extension);
        if (safeName.isBlank() || expectedType == null) throw bad("UNSUPPORTED_DOCUMENT", "Only PDF, JPEG, and PNG documents are allowed");
        String declaredType = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);
        if (!expectedType.equals(declaredType)) throw bad("DOCUMENT_TYPE_MISMATCH", "The filename extension and declared content type do not match");
        byte[] signature = new byte[8];
        int read;
        try (InputStream input = file.getInputStream()) { read = input.read(signature); }
        catch (IOException e) { throw bad("DOCUMENT_READ_FAILED", "The uploaded document could not be read"); }
        if (!matches(expectedType, signature, read)) throw bad("DOCUMENT_TYPE_MISMATCH", "The document content does not match its file type");
        return new ValidatedFile(safeName, extension, expectedType);
    }
    private static boolean matches(String type, byte[] bytes, int length) {
        if ("application/pdf".equals(type)) return length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
        if ("image/jpeg".equals(type)) return length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
        return length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && (bytes[4] & 0xff) == 0x0d && (bytes[5] & 0xff) == 0x0a && (bytes[6] & 0xff) == 0x1a && (bytes[7] & 0xff) == 0x0a;
    }
    private static OnboardingException bad(String code, String message) { return new OnboardingException(HttpStatus.BAD_REQUEST, code, message); }
}
