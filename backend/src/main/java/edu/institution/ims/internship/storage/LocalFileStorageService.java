package edu.institution.ims.internship.storage;

import edu.institution.ims.internship.OnboardingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;

@Service
public class LocalFileStorageService implements FileStorageService {
    private final Path root;
    public LocalFileStorageService(@Value("${app.file-storage.root}") String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
        try { Files.createDirectories(this.root); }
        catch (IOException e) { throw new IllegalStateException("Could not initialize document storage", e); }
    }
    @Override public String store(InputStream input, String extension) {
        String key = UUID.randomUUID() + "." + extension;
        Path destination = resolve(key);
        try { Files.copy(input, destination); return key; }
        catch (IOException e) { throw new OnboardingException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_FAILED", "The document could not be stored"); }
    }
    @Override public StoredFile load(String storageKey) {
        Path file = resolve(storageKey);
        if (!Files.isRegularFile(file)) throw new OnboardingException(HttpStatus.NOT_FOUND, "DOCUMENT_FILE_NOT_FOUND", "The stored document was not found");
        try { return new StoredFile(new FileSystemResource(file), Files.size(file)); }
        catch (IOException e) { throw new OnboardingException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_READ_FAILED", "The document could not be read"); }
    }
    @Override public void delete(String storageKey) {
        try { Files.deleteIfExists(resolve(storageKey)); }
        catch (IOException ignored) { }
    }
    private Path resolve(String key) {
        if (key == null || !key.matches("[0-9a-fA-F-]{36}\\.(pdf|docx|jpg|jpeg|png)"))
            throw new OnboardingException(HttpStatus.BAD_REQUEST, "INVALID_STORAGE_KEY", "Invalid document storage key");
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) throw new OnboardingException(HttpStatus.BAD_REQUEST, "INVALID_STORAGE_KEY", "Invalid document storage key");
        return path;
    }
}
