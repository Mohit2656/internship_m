package edu.institution.ims.monthlyprogress;

import edu.institution.ims.security.*;
import edu.institution.ims.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:photo-multipart;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class MonthlyProgressMultipartLimitTest {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;
    @Autowired MultipartProperties multipart;
    @Test void realServletRejectsOversizedFileAndOversizedCombinedRequest() throws Exception {
        assertThat(multipart.getMaxFileSize().toBytes()).isEqualTo(20L * 1024 * 1024);
        assertThat(multipart.getMaxRequestSize().toBytes()).isEqualTo(21L * 1024 * 1024);
        User student = users.save(new User("multipart" + UUID.randomUUID() + "@example.edu", encoder.encode("MultipartPassword!23"), Role.STUDENT, true));
        String cookie = "ims_auth=" + jwt.create(UserPrincipal.from(student));
        assertRejected(cookie, 20 * 1024 * 1024 + 1, 16);
        assertRejected(cookie, 12 * 1024 * 1024, 10 * 1024 * 1024);
    }
    private void assertRejected(String cookie, int photoSize, int descriptionSize) throws Exception {
        String boundary = "IMS-Upload-Boundary";
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"large.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        body.write(new byte[photoSize]);
        body.write(("\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"workDescription\"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        body.write(new byte[descriptionSize]);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/student/monthly-progress/1"))
            .header("Cookie", cookie).header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("configured file or total request size limit");
    }
}
