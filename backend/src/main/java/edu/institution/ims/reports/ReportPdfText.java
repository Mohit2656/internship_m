package edu.institution.ims.reports;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.font.PDFont;

/** Width-based wrapping shared by report documents, including unbroken tokens. */
public final class ReportPdfText {
    private ReportPdfText() {}

    public static String clean(String value, PDFont font) throws IOException {
        String text = value == null ? "Not available" : value.replace('\t', ' ');
        try { font.encode(text); }
        catch (IllegalArgumentException exception) {
            throw new UnrenderableTextException("Times New Roman cannot render a character in this report. Please check the report text.", exception);
        }
        return text;
    }

    public static List<String> wrap(String value, PDFont font, float size, float maxWidth) throws IOException {
        if (size <= 0 || maxWidth <= 0) throw new IllegalArgumentException("Text size and width must be positive");
        List<String> lines = new ArrayList<>();
        for (String paragraph : (value == null ? "Not available" : value).replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            String line = "";
            if (paragraph.isBlank()) { lines.add(""); continue; }
            for (String word : paragraph.trim().split("\\s+")) {
                word = clean(word, font);
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (width(candidate, font, size) <= maxWidth) { line = candidate; continue; }
                if (!line.isEmpty()) { lines.add(line); line = ""; }
                for (int offset = 0; offset < word.length();) {
                    int codePoint = word.codePointAt(offset);
                    String character = new String(Character.toChars(codePoint));
                    if (width(character, font, size) > maxWidth) throw new IOException("Report column is too narrow to render its text");
                    if (!line.isEmpty() && width(line + character, font, size) > maxWidth) {
                        lines.add(line); line = "";
                    }
                    line += character;
                    offset += Character.charCount(codePoint);
                }
            }
            lines.add(line);
        }
        return lines;
    }

    private static float width(String value, PDFont font, float size) throws IOException {
        return font.getStringWidth(value) / 1000 * size;
    }

    public static final class UnrenderableTextException extends IOException {
        public UnrenderableTextException(String message, Throwable cause) { super(message, cause); }
    }
}
