package edu.institution.ims.reports;

import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

/** Standard PDF fonts require no font files, OS installation or deployment configuration. */
public final class ReportPdfFonts {
    private ReportPdfFonts() {}
    public record Fonts(PDFont regular, PDFont bold) {}

    public static Fonts load(PDDocument document) throws IOException {
        return new Fonts(PDType1Font.TIMES_ROMAN, PDType1Font.TIMES_BOLD);
    }
}
