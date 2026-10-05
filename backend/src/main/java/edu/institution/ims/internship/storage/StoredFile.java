package edu.institution.ims.internship.storage;

import org.springframework.core.io.Resource;

public record StoredFile(Resource resource, long size) {}
