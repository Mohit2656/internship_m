package edu.institution.ims.reports;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.font.PDFont;

/** Width-based wrapping shared by report documents, including unbroken tokens. */
public final class ReportPdfText {
    private ReportPdfText() {}

    public static String clean(String value, PDFont font) throws IOException {
        if (value == null) return "Not available";
        String text = value
                .replace('\t', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00A0', ' ')
                .replace('\u200B', ' ')
                .replace('\uFEFF', ' ')
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u201A', '\'')
                .replace('\u201C', '"')
                .replace('\u201D', '"')
                .replace('\u201E', '"')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace("\u2026", "...")
                .replace('\u2022', '*')
                .replace('\u25CF', '*')
                .replace('\u25CB', '*')
                .replace('\u25AA', '-')
                .replace('\u25AB', '-')
                .replace("\u20B9", "Rs.");

        StringBuilder safe = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            try {
                font.encode(String.valueOf(c));
                safe.append(c);
            } catch (IllegalArgumentException exception) {
                safe.append(' ');
            }
        }
        return safe.toString();
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
