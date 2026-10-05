package edu.institution.ims.opportunity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import java.time.Duration;
import java.util.List;

@Component
public class HttpOpportunityMatchClient implements OpportunityMatchClient {
    private final RestClient client;
    public HttpOpportunityMatchClient(@Value("${app.opportunity-ai.base-url:${app.resume-ai.base-url}}") String baseUrl,
            @Value("${app.opportunity-ai.connect-timeout-seconds:3}") long connectTimeout,
            @Value("${app.opportunity-ai.read-timeout-seconds:120}") long readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeout)); factory.setReadTimeout(Duration.ofSeconds(readTimeout));
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
    @Override public OpportunityMatchResult match(OpportunityMatchRequest request) {
        try {
            OpportunityMatchResult response = client.post().uri("/internal/opportunities/match").body(request).retrieve().body(OpportunityMatchResult.class);
            if (response == null) throw new OpportunityMatchClientException("AI service returned an empty response", null);
            return response.validated();
        } catch (RestClientException | IllegalArgumentException e) {
            throw new OpportunityMatchClientException("Opportunity matching service request failed", e);
        }
    }
    @Override public List<OpportunityMatchResult> matchAll(List<OpportunityMatchRequest> requests) {
        try {
            BatchResponse response = client.post().uri("/internal/opportunities/match-batch").body(new BatchRequest(requests)).retrieve().body(BatchResponse.class);
            if (response == null || response.matches() == null || response.matches().size() != requests.size())
                throw new OpportunityMatchClientException("AI service returned an invalid batch response", null);
            return response.matches().stream().map(OpportunityMatchResult::validated).toList();
        } catch (RestClientException | IllegalArgumentException e) {
            throw new OpportunityMatchClientException("Opportunity matching service batch request failed", e);
        }
    }
    private record BatchRequest(List<OpportunityMatchRequest> matches) {}
    private record BatchResponse(List<OpportunityMatchResult> matches) {}
}
