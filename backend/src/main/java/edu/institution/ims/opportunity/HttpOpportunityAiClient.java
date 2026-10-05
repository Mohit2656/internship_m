package edu.institution.ims.opportunity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import java.time.Duration;

@Component
public class HttpOpportunityAiClient implements OpportunityAiClient {
    private final RestClient client;
    public HttpOpportunityAiClient(@Value("${app.opportunity-ai.base-url:${app.resume-ai.base-url}}") String baseUrl,
            @Value("${app.opportunity-ai.connect-timeout-seconds:3}") long connectTimeout,
            @Value("${app.opportunity-ai.read-timeout-seconds:120}") long readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeout)); factory.setReadTimeout(Duration.ofSeconds(readTimeout));
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
    @Override public AiOpportunityResult analyze(String title, String description) {
        try {
            AiOpportunityResult response = client.post().uri("/internal/opportunities/analyze")
                    .body(new Request(title, description)).retrieve().body(AiOpportunityResult.class);
            if (response == null) throw new OpportunityAiClientException("AI service returned an empty response", null);
            return response;
        } catch (RestClientException e) { throw new OpportunityAiClientException("Opportunity AI service request failed", e); }
    }
    private record Request(String title, String description) {}
}
