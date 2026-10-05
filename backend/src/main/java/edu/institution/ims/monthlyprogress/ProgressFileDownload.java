package edu.institution.ims.monthlyprogress;

import org.springframework.core.io.Resource;

public record ProgressFileDownload(Resource resource, byte[] bytes, String filename, String contentType, long size) {
    public static ProgressFileDownload resource(Resource resource, String filename, String contentType, long size) {
        return new ProgressFileDownload(resource, null, filename, contentType, size);
    }
    public static ProgressFileDownload bytes(byte[] bytes, String filename, String contentType) {
        return new ProgressFileDownload(null, bytes, filename, contentType, bytes.length);
    }
}
