package edu.institution.ims.reports;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ReportPdfFontDeploymentTest {
    @TempDir Path temporaryDirectory;

    @Test
    void pdfExportsRegularAndBoldTextWithoutFontDirectoryConfiguration() throws Exception {
        Path pdf = exportInSeparateJvm(null);

        assertExportContainsBothFaces(pdf);
    }

    @Test
    void pdfExportsRegularAndBoldTextWhenLegacyFontDirectoryDoesNotExist() throws Exception {
        Path missingDirectory = temporaryDirectory.resolve("missing-licensed-fonts");
        assertThat(missingDirectory).doesNotExist();

        Path pdf = exportInSeparateJvm(missingDirectory.toAbsolutePath().toString());

        assertExportContainsBothFaces(pdf);
        assertThat(missingDirectory).doesNotExist();
    }

    private Path exportInSeparateJvm(String configuredDirectory) throws Exception {
        // A separate JVM lets this test set/remove the real deployment environment
        // variable without reflection hacks or changing the test runner environment.
        Path pdf = temporaryDirectory.resolve("export.pdf");
        Path processOutput = temporaryDirectory.resolve("export-process.log");
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        if (!Files.isRegularFile(java)) java = java.resolveSibling("java.exe");
        String classpath = System.getProperty("surefire.test.class.path",
                System.getProperty("java.class.path"));
        var builder = new ProcessBuilder(java.toString(), "-cp", classpath,
                FontExportProbe.class.getName(), pdf.toString(),
                configuredDirectory == null ? "" : configuredDirectory);
        if (configuredDirectory == null) builder.environment().remove("REPORT_PDF_FONT_DIRECTORY");
        else builder.environment().put("REPORT_PDF_FONT_DIRECTORY", configuredDirectory);
        builder.redirectErrorStream(true).redirectOutput(processOutput.toFile());

        Process process = builder.start();
        try {
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            String output = Files.readString(processOutput);
            assertThat(finished).withFailMessage("Font export process timed out: %s", output).isTrue();
            assertThat(process.exitValue())
                    .withFailMessage("Font export failed without deployment font files: %s", output)
                    .isZero();
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
        assertThat(pdf).isRegularFile();
        return pdf;
    }

    private void assertExportContainsBothFaces(Path file) throws Exception {
        try (PDDocument pdf = PDDocument.load(file.toFile())) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(pdf))
                    .contains("Regular export body", "Bold export heading");
            Set<String> fontNames = new HashSet<>();
            for (var resourceName : pdf.getPage(0).getResources().getFontNames()) {
                var font = pdf.getPage(0).getResources().getFont(resourceName);
                assertThat(font.getStringWidth("export")).isPositive();
                fontNames.add(font.getName());
            }
            assertThat(fontNames).containsExactlyInAnyOrder("Times-Roman", "Times-Bold");
        }
    }

    public static final class FontExportProbe {
        public static void main(String[] arguments) throws Exception {
            String legacyProperty = "report.pdf.font-directory";
            if (arguments[1].isEmpty()) System.clearProperty(legacyProperty);
            else System.setProperty(legacyProperty, arguments[1]);

            try (var document = new PDDocument()) {
                var fonts = ReportPdfFonts.load(document);
                PDPage page = new PDPage();
                document.addPage(page);
                try (var content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.newLineAtOffset(50, 700);
                    content.setFont(fonts.regular(), 12);
                    content.showText("Regular export body");
                    content.newLineAtOffset(0, -24);
                    content.setFont(fonts.bold(), 12);
                    content.showText("Bold export heading");
                    content.endText();
                }
                document.save(arguments[0]);
            }
        }
    }
}
