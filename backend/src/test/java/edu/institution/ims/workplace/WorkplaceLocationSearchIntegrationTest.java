package edu.institution.ims.workplace;

import edu.institution.ims.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkplaceLocationSearchIntegrationTest {
    private static final String PATH = "/api/v1/workplace-locations/search";
    @Autowired MockMvc mvc;
    @MockitoBean WorkplaceLocationSearchService service;

    @Test void anonymousRequestsRequireAuthentication() throws Exception {
        mvc.perform(get(PATH).param("q", "Nagpur")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(roles = "MENTOR") void mentorsCannotUseRegistrationSearch() throws Exception {
        mvc.perform(get(PATH).param("q", "Nagpur")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(roles = "STUDENT") void studentReceivesOnlyNormalizedResultFields() throws Exception {
        when(service.searchAsync("Nagpur", null, null)).thenReturn(CompletableFuture.completedFuture(List.of(new WorkplaceLocationSearchResult("Branch", "Branch, Nagpur", 21.125, 79.0875))));
        var result = mvc.perform(get(PATH).param("q", "Nagpur")).andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Branch"))
                .andExpect(jsonPath("$[0].address").value("Branch, Nagpur"))
                .andExpect(jsonPath("$[0].latitude").value(21.125))
                .andExpect(jsonPath("$[0].longitude").value(79.0875))
                .andExpect(jsonPath("$[0].matchQuality").value("APPROXIMATE_ADDRESS"))
                .andExpect(jsonPath("$[0].placeId").doesNotExist());
    }

    @Test @WithMockUser(roles = "ADMIN") void administratorsCanSearchForWorkplaceCorrections() throws Exception {
        when(service.searchAsync("Nagpur", null, null)).thenReturn(CompletableFuture.completedFuture(List.of()));
        var result = mvc.perform(get(PATH).param("q", "Nagpur")).andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void requiredPasswordChangesStillPreventSearch() throws Exception {
        var principal = new UserPrincipal(1L, "student@example.test", "", true, "STUDENT", true, 0);
        mvc.perform(get(PATH).param("q", "Nagpur").with(user(principal)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(roles = "STUDENT") void missingQueryUsesNormalClientErrorResponse() throws Exception {
        when(service.searchAsync("", null, null)).thenThrow(new WorkplaceLocationSearchException(HttpStatus.BAD_REQUEST, "INVALID_WORKPLACE_SEARCH", "Enter a search."));
        mvc.perform(get(PATH)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_WORKPLACE_SEARCH"));
    }

    @Test @WithMockUser(roles = "STUDENT") void throttleIncludesRetryAfterAndApiError() throws Exception {
        when(service.searchAsync("Nagpur", null, null)).thenReturn(CompletableFuture.failedFuture(new WorkplaceLocationSearchException(HttpStatus.TOO_MANY_REQUESTS, "WORKPLACE_SEARCH_RATE_LIMITED", "Wait a moment.")));
        var result = mvc.perform(get(PATH).param("q", "Nagpur")).andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.status").value(429)).andExpect(jsonPath("$.code").value("WORKPLACE_SEARCH_RATE_LIMITED"));
    }

    @Test @WithMockUser(roles = "ADMIN") void providerFailureUsesRecoverableUnavailableResponse() throws Exception {
        when(service.searchAsync("Nagpur", null, null)).thenReturn(CompletableFuture.failedFuture(new WorkplaceLocationSearchException(HttpStatus.SERVICE_UNAVAILABLE, "WORKPLACE_SEARCH_UNAVAILABLE", "Retry or use the map.")));
        var result = mvc.perform(get(PATH).param("q", "Nagpur")).andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("WORKPLACE_SEARCH_UNAVAILABLE"));
    }

    @Test @WithMockUser(roles = "STUDENT") void forwardsOptionalValidBiasToSearchService() throws Exception {
        when(service.searchAsync("Nagpur", 21.1498, 79.082)).thenReturn(CompletableFuture.completedFuture(List.of()));
        var result = mvc.perform(get(PATH).param("q", "Nagpur").param("lat", "21.1498").param("lon", "79.082"))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(service).searchAsync("Nagpur", 21.1498, 79.082);
    }
}
