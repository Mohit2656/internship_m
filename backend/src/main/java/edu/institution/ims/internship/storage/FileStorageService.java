package edu.institution.ims.internship.storage;

import java.io.InputStream;

public interface FileStorageService {
    String store(InputStream input, String extension);
    StoredFile load(String storageKey);
    void delete(String storageKey);
}
