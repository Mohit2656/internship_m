package edu.institution.ims.reports;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/** Loads the actual Times New Roman files and embeds them in each document. */
public final class ReportPdfFonts {
    private ReportPdfFonts() {}
    public record Fonts(PDFont regular, PDFont bold) {}

    public static Fonts load(PDDocument document) throws IOException {
        String configured = System.getProperty("report.pdf.font-directory");
        if (configured == null || configured.isBlank()) configured = System.getenv("REPORT_PDF_FONT_DIRECTORY");
        List<Path> directories = configured != null && !configured.isBlank()
                ? List.of(Path.of(configured))
                : List.of(Path.of(System.getenv().getOrDefault("WINDIR", "C:/Windows"), "Fonts"),
                          Path.of("/usr/share/fonts/truetype/msttcorefonts"),
                          Path.of("/usr/share/fonts/truetype/msttcore"),
                          Path.of("/Library/Fonts"));
        for (Path directory : directories) {
            Path regular = find(directory, "times.ttf", "Times_New_Roman.ttf", "Times New Roman.ttf");
            Path bold = find(directory, "timesbd.ttf", "Times_New_Roman_Bold.ttf", "Times New Roman Bold.ttf");
            if (regular == null || bold == null) continue;
            PDFont regularFont = PDType0Font.load(document, regular.toFile());
            PDFont boldFont = PDType0Font.load(document, bold.toFile());
            verify(regularFont); verify(boldFont);
            return new Fonts(regularFont, boldFont);
        }
        throw new FontUnavailableException("Times New Roman PDF fonts are unavailable. Configure REPORT_PDF_FONT_DIRECTORY with licensed times.ttf and timesbd.ttf files.");
    }

    private static Path find(Path directory, String... names) throws IOException {
        if (!Files.isDirectory(directory)) return null;
        try (var entries = Files.list(directory)) {
            return entries.filter(Files::isRegularFile)
                    .filter(path -> List.of(names).stream().anyMatch(name -> name.equalsIgnoreCase(path.getFileName().toString())))
                    .findFirst().orElse(null);
        }
    }

    private static void verify(PDFont font) throws FontUnavailableException {
        String name = font.getName().replaceAll("[^a-zA-Z]", "").toLowerCase(Locale.ROOT);
        if (!name.contains("timesnewroman") || !font.isEmbedded())
            throw new FontUnavailableException("Report PDF fonts must be embedded Times New Roman; the configured font does not match.");
    }

    public static final class FontUnavailableException extends IOException {
        public FontUnavailableException(String message) { super(message); }
    }
}
