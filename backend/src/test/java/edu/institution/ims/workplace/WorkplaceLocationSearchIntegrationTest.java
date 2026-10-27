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
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        when(service.search("Nagpur")).thenReturn(List.of(new WorkplaceLocationSearchResult("Branch", "Branch, Nagpur", 21.125, 79.0875)));
        mvc.perform(get(PATH).param("q", "Nagpur")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Branch"))
                .andExpect(jsonPath("$[0].address").value("Branch, Nagpur"))
                .andExpect(jsonPath("$[0].latitude").value(21.125))
                .andExpect(jsonPath("$[0].longitude").value(79.0875))
                .andExpect(jsonPath("$[0].placeId").doesNotExist());
    }

    @Test @WithMockUser(roles = "ADMIN") void administratorsCanSearchForWorkplaceCorrections() throws Exception {
        when(service.search("Nagpur")).thenReturn(List.of());
        mvc.perform(get(PATH).param("q", "Nagpur")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void requiredPasswordChangesStillPreventSearch() throws Exception {
        var principal = new UserPrincipal(1L, "student@example.test", "", true, "STUDENT", true, 0);
        mvc.perform(get(PATH).param("q", "Nagpur").with(user(principal)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(roles = "STUDENT") void missingQueryUsesNormalClientErrorResponse() throws Exception {
        when(service.search("")).thenThrow(new WorkplaceLocationSearchException(HttpStatus.BAD_REQUEST, "INVALID_WORKPLACE_SEARCH", "Enter a search."));
        mvc.perform(get(PATH)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_WORKPLACE_SEARCH"));
    }

    @Test @WithMockUser(roles = "STUDENT") void throttleIncludesRetryAfterAndApiError() throws Exception {
        when(service.search("Nagpur")).thenThrow(new WorkplaceLocationSearchException(HttpStatus.TOO_MANY_REQUESTS, "WORKPLACE_SEARCH_RATE_LIMITED", "Wait a moment."));
        mvc.perform(get(PATH).param("q", "Nagpur")).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.status").value(429)).andExpect(jsonPath("$.code").value("WORKPLACE_SEARCH_RATE_LIMITED"));
    }

    @Test @WithMockUser(roles = "ADMIN") void providerFailureUsesRecoverableUnavailableResponse() throws Exception {
        when(service.search("Nagpur")).thenThrow(new WorkplaceLocationSearchException(HttpStatus.SERVICE_UNAVAILABLE, "WORKPLACE_SEARCH_UNAVAILABLE", "Retry or use the map."));
        mvc.perform(get(PATH).param("q", "Nagpur")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("WORKPLACE_SEARCH_UNAVAILABLE"));
    }
}
