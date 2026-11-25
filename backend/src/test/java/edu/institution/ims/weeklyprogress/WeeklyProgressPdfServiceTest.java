package edu.institution.ims.weeklyprogress;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WeeklyProgressPdfServiceTest {
    @Test void generatedReportContainsSavedInternshipAndReviewData() throws Exception {
        var week=new WeeklyProgressService.Week(9L,22L,2,4L,"Bismeet Kukreja","UID-204",
                "Company B",3,LocalDate.of(2026,11,2),LocalDate.of(2026,11,8),true,
                "SUBMITTED","REJECTED","Implemented the saved attendance workflow.",2,
                Instant.parse("2026-11-08T10:00:00Z"),7L,"mentor@example.edu",
                Instant.parse("2026-11-09T10:00:00Z"),"Add test evidence.",null,null,5,4,4,List.of());

        byte[] bytes=new WeeklyProgressPdfService().generate(week);

        assertThat(bytes).startsWith("%PDF".getBytes());
        try(PDDocument document=PDDocument.load(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains(
                    "ST. VINCENT PALLOTTI COLLEGE OF ENGINEERING & TECHNOLOGY, NAGPUR",
                    "Department of Computer Engineering","WEEKLY PROGRESS REPORT","Bismeet Kukreja",
                    "UID-204","Company B","Internship 2","Week 3","Implemented the saved attendance workflow.",
                    "REJECTED","mentor@example.edu","Mentor feedback: Add test evidence.");
            assertThat(text).doesNotContain("Signature", "Student Signature", "Faculty Mentor Signature", "III Cell Incharge");
        }
    }
}
