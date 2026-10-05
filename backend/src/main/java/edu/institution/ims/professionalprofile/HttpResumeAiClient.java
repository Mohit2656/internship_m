package edu.institution.ims.professionalprofile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.*;
import org.springframework.web.client.*;
import java.io.IOException;
import java.time.Duration;

@Component
public class HttpResumeAiClient implements ResumeAiClient {
    private final RestClient client;

    public HttpResumeAiClient(@Value("${app.resume-ai.base-url}") String baseUrl,
            @Value("${app.resume-ai.connect-timeout-seconds:3}") long connectTimeout,
            @Value("${app.resume-ai.read-timeout-seconds:120}") long readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeout));
        factory.setReadTimeout(Duration.ofSeconds(readTimeout));
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public AiResumeResult analyze(Resource file, String filename, String contentType) {
        try {
            byte[] bytes = file.getInputStream().readAllBytes();
            ByteArrayResource body = new ByteArrayResource(bytes) {
                @Override public String getFilename() { return filename; }
            };
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.parseMediaType(contentType));
            MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
            multipart.add("file", new HttpEntity<>(body, fileHeaders));
            AiResumeResult response = client.post().uri("/internal/resume/analyze")
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(multipart).retrieve().body(AiResumeResult.class);
            if (response == null) throw new ResumeAiClientException("AI service returned an empty response", null);
            return response;
        } catch (IOException | RestClientException e) {
            throw new ResumeAiClientException("Resume AI service request failed", e);
        }
    }
}
