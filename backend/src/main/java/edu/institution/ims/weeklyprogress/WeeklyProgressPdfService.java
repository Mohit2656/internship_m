package edu.institution.ims.weeklyprogress;

import edu.institution.ims.internship.InternshipDetailsException;
import edu.institution.ims.reports.ReportPdfFonts;
import edu.institution.ims.reports.ReportPdfText;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class WeeklyProgressPdfService {
    private static final float MARGIN=52;
    private static final float WIDTH=PDRectangle.A4.getWidth()-(MARGIN*2);
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd MMM yyyy",Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME=DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a",Locale.ENGLISH)
            .withZone(ZoneId.of("Asia/Kolkata"));

    public byte[] generate(WeeklyProgressService.Week week) {
        return generate(week, week.internshipNumber() == 2, week.reviewerEmail());
    }

    public byte[] generate(WeeklyProgressService.Week week, boolean hasTwoInternships, String facultyMentor) {
        try(PDDocument document=new PDDocument(); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            var fonts=ReportPdfFonts.load(document);
            Writer writer=new Writer(document,fonts);
            writer.center("ST. VINCENT PALLOTTI COLLEGE OF ENGINEERING & TECHNOLOGY, NAGPUR",fonts.bold(),12);
            writer.center("Department of Computer Engineering",fonts.regular(),10);
            writer.space(10);
            writer.center("WEEKLY PROGRESS REPORT",fonts.bold(),15);
            writer.center("Week "+week.weekNumber(),fonts.bold(),12);
            writer.rule();
            writer.label("Student Name",week.studentName());
            writer.label("UID / Roll Number",week.uid());
            writer.label("Company",week.companyName());
            writer.label("Faculty Mentor", facultyMentor != null && !facultyMentor.isBlank() ? facultyMentor : (week.reviewerEmail() != null ? week.reviewerEmail() : "Not assigned"));
            if (hasTwoInternships) {
                writer.label("Selected Internship", "Internship " + week.internshipNumber());
            }
            writer.label("Week Number",String.valueOf(week.weekNumber()));
            writer.label("Reporting Period",DATE.format(week.periodStart())+" - "+DATE.format(week.periodEnd()));
            writer.label("Submission Date",DATE_TIME.format(week.submittedAt()));
            writer.label("Report Status",week.status());
            writer.label("Revision",String.valueOf(week.revision()));
            if(week.reviewedAt()!=null) {
                writer.label("Reviewed At",DATE_TIME.format(week.reviewedAt()));
                writer.label("Reviewer",week.reviewerEmail());
            }
            if(week.rejectionReason()!=null && !week.rejectionReason().isBlank()) {
                writer.space(6);
                writer.paragraph("Mentor feedback: " + week.rejectionReason(), 10);
            }

            writer.heading("WORK PERFORMED DURING THE WEEK");
            writer.paragraph(week.workDescription(),10);

            writer.close();
            writer.pageNumbers();
            document.save(output);
            return output.toByteArray();
        } catch(ReportPdfText.UnrenderableTextException exception) {
            throw new InternshipDetailsException(HttpStatus.UNPROCESSABLE_ENTITY,"REPORT_TEXT_UNSUPPORTED",exception.getMessage());
        } catch(IOException exception) {
            throw new InternshipDetailsException(HttpStatus.INTERNAL_SERVER_ERROR,"PDF_GENERATION_FAILED","The weekly progress PDF could not be generated");
        }
    }

    private static final class Writer {
        private final PDDocument document;
        private final ReportPdfFonts.Fonts fonts;
        private PDPageContentStream stream;
        private float y;
        Writer(PDDocument document,ReportPdfFonts.Fonts fonts) throws IOException { this.document=document; this.fonts=fonts; newPage(); }
        void newPage() throws IOException { if(stream!=null) stream.close(); PDPage page=new PDPage(PDRectangle.A4); document.addPage(page); stream=new PDPageContentStream(document,page); y=page.getMediaBox().getHeight()-MARGIN; }
        void ensure(float height) throws IOException { if(y-height<MARGIN) newPage(); }
        void space(float amount) throws IOException { ensure(amount); y-=amount; }
        void center(String value,PDFont font,float size) throws IOException { for(String line:ReportPdfText.wrap(value,font,size,WIDTH)){ ensure(size+5); float textWidth=font.getStringWidth(line)/1000*size; text(line,MARGIN+(WIDTH-textWidth)/2,font,size); y-=size+5; } }
        void text(String value,float x,PDFont font,float size) throws IOException { stream.beginText(); stream.setFont(font,size); stream.newLineAtOffset(x,y); stream.showText(ReportPdfText.clean(value==null?"Not available":value,font)); stream.endText(); }
        void rule() throws IOException { space(5); stream.moveTo(MARGIN,y); stream.lineTo(MARGIN+WIDTH,y); stream.stroke(); space(14); }
        void heading(String value) throws IOException { ensure(72); space(12); text(value,MARGIN,fonts.bold(),11); y-=16; rule(); }
        void label(String label,String value) throws IOException { float labelWidth=132; var labels=ReportPdfText.wrap(label+":",fonts.bold(),10,labelWidth-8); var values=ReportPdfText.wrap(value==null?"Not available":value,fonts.regular(),10,WIDTH-labelWidth); int lines=Math.max(labels.size(),values.size()); if (lines * 15 <= PDRectangle.A4.getHeight() - MARGIN * 2) ensure(lines*15); for(int i=0;i<lines;i++){ ensure(15); if(i<labels.size()) text(labels.get(i),MARGIN,fonts.bold(),10); if(i<values.size()) text(values.get(i),MARGIN+labelWidth,fonts.regular(),10); y-=15; } }
        void paragraph(String value,float size) throws IOException { if (value == null || value.isBlank()) { ensure(size + 5); text("Not available", MARGIN, fonts.regular(), size); y -= size + 5; return; } for(String line:ReportPdfText.wrap(value,fonts.regular(),size,WIDTH)){ ensure(size+5); if (!line.isEmpty()) { text(line,MARGIN,fonts.regular(),size); } y-=size+5; } }
        void close() throws IOException { if (stream != null) { stream.close(); stream=null; } }
        void pageNumbers() throws IOException { int count=document.getNumberOfPages(); for(int i=0;i<count;i++){ String footer="Page "+(i+1)+" of "+count; float width=fonts.regular().getStringWidth(footer)/1000*9; try(var footerStream=new PDPageContentStream(document,document.getPage(i),PDPageContentStream.AppendMode.APPEND,true,true)){ footerStream.beginText(); footerStream.setFont(fonts.regular(),9); footerStream.newLineAtOffset((PDRectangle.A4.getWidth()-width)/2,28); footerStream.showText(footer); footerStream.endText(); } } }
    }
}
