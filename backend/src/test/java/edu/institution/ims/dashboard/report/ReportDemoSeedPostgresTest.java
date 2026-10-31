package edu.institution.ims.dashboard.report;

import java.nio.file.*;
import java.util.*;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.annotation.Commit;

/** Explicit opt-in seeder for the user's retained, isolated LOCAL demonstration. */
@EnabledIfEnvironmentVariable(named="REPORT_DEMO_RETAIN",matches="true")
@TestPropertySource(properties={"spring.datasource.url=${EXIF_TEST_DB_URL}","spring.datasource.username=${EXIF_TEST_DB_USERNAME}","spring.datasource.password=${EXIF_TEST_DB_PASSWORD}","spring.datasource.driver-class-name=org.postgresql.Driver","spring.flyway.schemas=${EXIF_TEST_DB_SCHEMA}","spring.jpa.properties.hibernate.default_schema=${EXIF_TEST_DB_SCHEMA}"})
class ReportDemoSeedPostgresTest extends ReportFilterRegressionIntegrationTest {
    @Test @Commit void leaveLocalDummyDataForManualTesting() throws Exception {
        String schema=System.getenv("EXIF_TEST_DB_SCHEMA");
        if(schema==null || !schema.matches("ims_report_demo_[0-9a-f]{32}"))throw new IllegalStateException("A separate local demo schema is required");
        Path storage=Path.of(Objects.requireNonNull(System.getenv("REPORT_DEMO_STORAGE"))).toAbsolutePath().normalize();
        if(!storage.getFileName().toString().equals("report-demo-storage") || !storage.getParent().getFileName().toString().equals(".local-run"))throw new IllegalStateException("A dedicated demo storage folder is required");
        Files.createDirectories(storage);
        byte[] pdf;
        try(var document=new PDDocument();var bytes=new ByteArrayOutputStream()) {
            var page=new PDPage();document.addPage(page);
            try(var content=new PDPageContentStream(document,page)){content.beginText();content.setFont(PDType1Font.HELVETICA_BOLD,16);content.newLineAtOffset(50,720);content.showText("TEST REPORT DOCUMENT - LOCAL DEMO ONLY");content.endText();}
            document.save(bytes);pdf=bytes.toByteArray();
        }
        for(var row:jdbc.queryForList("select id,storage_key from internship_documents")) {
            String key=(String)row.get("storage_key");
            if(!key.matches("[0-9a-f-]{36}[.]pdf"))throw new IllegalStateException("Unexpected demo document key");
            Files.write(storage.resolve(key),pdf);jdbc.update("update internship_documents set file_size=? where id=?",pdf.length,row.get("id"));
        }
        var picture=new java.awt.image.BufferedImage(320,120,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics=picture.createGraphics();graphics.setColor(java.awt.Color.WHITE);graphics.fillRect(0,0,320,120);graphics.setColor(java.awt.Color.BLUE);graphics.drawString("TEST REPORT PHOTO - LOCAL DEMO",20,60);graphics.dispose();
        var photoBytes=new ByteArrayOutputStream();javax.imageio.ImageIO.write(picture,"png",photoBytes);byte[] png=photoBytes.toByteArray();
        for(var row:jdbc.queryForList("select id from monthly_progress_reports")) {
            String key=UUID.randomUUID()+".png";Files.write(storage.resolve(key),png);
            jdbc.update("update monthly_progress_reports set photo_storage_key=?,photo_size=? where id=?",key,png.length,row.get("id"));
        }
        List<Map<String,Object>> rows=new ArrayList<>();
        for(Dummy d:students){Map<String,Object> row=new LinkedHashMap<>();row.put("id",d.user.getId());row.put("name",d.name());row.put("uid",d.uid());row.put("email",d.user.getEmail());row.put("sessionId",d.session);row.put("profileYear",d.year);row.put("mentorId",d.mentor);row.put("company",d.company);row.put("completionStatus",d.completion);row.put("workflow",d.workflow);row.put("active",d.active);rows.add(row);}
        Path file=Path.of(Objects.requireNonNull(System.getenv("REPORT_DEMO_MANIFEST")));Files.createDirectories(file.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(file.toFile(),Map.of("schema",schema,"asOf",TODAY.toString(),"dummyStudents",60,"frontendUrl","http://localhost:3010","adminEmail",admin.getEmail(),"mentorAEmail",mentorA.getEmail(),"mentorBEmail",mentorB.getEmail(),"password",PASSWORD,"students",rows));
    }
}
