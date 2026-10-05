package edu.institution.ims.studentaccounts;

import edu.institution.ims.studentprofile.StudentProfileRepository;
import edu.institution.ims.user.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class StudentAccountImportService {
    private static final List<String> HEADERS = List.of("Name", "UID", "Email ID", "Semester");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final UserRepository users; private final StudentAccountRepository accounts; private final StudentProfileRepository profiles;
    private final PasswordEncoder encoder; private final TransactionTemplate transactions;
    public StudentAccountImportService(UserRepository users, StudentAccountRepository accounts, StudentProfileRepository profiles, PasswordEncoder encoder,
            PlatformTransactionManager transactionManager) {
        this.users = users; this.accounts = accounts; this.profiles = profiles; this.encoder = encoder; this.transactions = new TransactionTemplate(transactionManager);
    }

    public byte[] template() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Row header = workbook.createSheet("Student Accounts").createRow(0);
            for (int i = 0; i < HEADERS.size(); i++) header.createCell(i).setCellValue(HEADERS.get(i));
            workbook.write(output); return output.toByteArray();
        } catch (IOException e) { throw new IllegalStateException("Could not create the Excel template"); }
    }

    public BulkImportResponse importWorkbook(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
            throw new IllegalArgumentException("Upload a non-empty .xlsx file");
        List<ImportRowResult> results = new ArrayList<>(); Set<String> uploadEmails = new HashSet<>(); Set<String> uploadUids = new HashSet<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null) throw new IllegalArgumentException("The Excel workbook has no worksheet");
            validateHeaders(sheet.getRow(0)); DataFormatter formatter = new DataFormatter();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex); if (isBlank(row, formatter)) continue;
                int displayRow = rowIndex + 1; Candidate candidate = candidate(row, formatter);
                String problem = validate(candidate);
                if (problem != null) { results.add(result(displayRow, candidate, "INVALID", problem)); continue; }
                if (!uploadEmails.add(candidate.email())) { results.add(result(displayRow, candidate, "INVALID", "Duplicate email in uploaded file")); continue; }
                if (!uploadUids.add(candidate.uid())) { results.add(result(displayRow, candidate, "INVALID", "Duplicate UID in uploaded file")); continue; }
                if (users.existsByEmailIgnoreCase(candidate.email()) || profiles.existsByUidIgnoreCase(candidate.uid()) || accounts.existsByUidIgnoreCase(candidate.uid())) {
                    results.add(result(displayRow, candidate, "EXISTING", "Email or UID already exists")); continue;
                }
                try {
                    transactions.executeWithoutResult(status -> create(candidate));
                    results.add(result(displayRow, candidate, "CREATED", null));
                } catch (DataIntegrityViolationException e) { results.add(result(displayRow, candidate, "EXISTING", "Email or UID already exists")); }
                  catch (RuntimeException e) { results.add(result(displayRow, candidate, "INVALID", "Account could not be created")); }
            }
        } catch (IllegalArgumentException e) { throw e; }
          catch (Exception e) { throw new IllegalArgumentException("The uploaded file is not a valid .xlsx workbook"); }
        int created = (int) results.stream().filter(r -> r.status().equals("CREATED")).count();
        int existing = (int) results.stream().filter(r -> r.status().equals("EXISTING")).count();
        int invalid = (int) results.stream().filter(r -> r.status().equals("INVALID")).count();
        return new BulkImportResponse(results.size(), created, existing, invalid, List.copyOf(results));
    }

    private void create(Candidate candidate) {
        User user = users.saveAndFlush(new User(candidate.email(), encoder.encode(candidate.uid()), Role.STUDENT, true));
        user.requirePasswordChange(); users.saveAndFlush(user);
        accounts.saveAndFlush(new StudentAccount(user, candidate.name(), candidate.uid(), candidate.semester()));
    }
    private static void validateHeaders(Row row) {
        if (row == null) throw new IllegalArgumentException("The Excel file is missing the required header row");
        DataFormatter formatter = new DataFormatter();
        for (int i = 0; i < HEADERS.size(); i++) if (!HEADERS.get(i).equalsIgnoreCase(value(row, i, formatter)))
            throw new IllegalArgumentException("The Excel headers must be: Name, UID, Email ID, Semester");
    }
    private static Candidate candidate(Row row, DataFormatter formatter) {
        String name = value(row, 0, formatter).trim(); String uid = value(row, 1, formatter).trim().toUpperCase(Locale.ROOT);
        String email = value(row, 2, formatter).trim().toLowerCase(Locale.ROOT); String semester = value(row, 3, formatter).trim();
        Integer value; try { value = Integer.valueOf(semester); } catch (NumberFormatException e) { value = null; }
        return new Candidate(name, uid, email, value);
    }
    private static String validate(Candidate c) {
        if (c.name().isBlank() || c.name().length() > 150) return "Name is required and must be 150 characters or fewer";
        if (c.uid().isBlank() || c.uid().length() > 50) return "UID is required and must be 50 characters or fewer";
        if (c.email().isBlank() || c.email().length() > 254 || !EMAIL.matcher(c.email()).matches()) return "Email ID is invalid";
        if (c.semester() == null || c.semester() < 1 || c.semester() > 8) return "Semester must be between 1 and 8";
        return null;
    }
    private static String value(Row row, int column, DataFormatter formatter) { Cell cell = row == null ? null : row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL); return cell == null ? "" : formatter.formatCellValue(cell); }
    private static boolean isBlank(Row row, DataFormatter formatter) { for (int i = 0; i < HEADERS.size(); i++) if (!value(row, i, formatter).isBlank()) return false; return true; }
    private static ImportRowResult result(int row, Candidate candidate, String status, String reason) { return new ImportRowResult(row, candidate.name(), candidate.uid(), candidate.email(), status, reason); }
    private record Candidate(String name, String uid, String email, Integer semester) {}
}
