package edu.institution.ims.studentaccounts;

import java.util.List;

public record BulkImportResponse(int totalRows, int accountsCreated, int alreadyExisting, int invalidRows, List<ImportRowResult> rows) {}
