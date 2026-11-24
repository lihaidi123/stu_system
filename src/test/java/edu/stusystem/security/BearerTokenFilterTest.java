package edu.stusystem.security;

import edu.stusystem.service.IdentityService;
import edu.stusystem.service.AtomicRateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BearerTokenFilterTest {
    @Test
    void databaseFailureReturns503WithoutPassingRequestToApplication() throws Exception {
        JwtService jwt = new JwtService("stage-4-filter-test-secret-at-least-32-bytes");
        String token = jwt.issue(new CurrentUser(42, "filter-test", "Filter Test", "ADMIN", 0, null));
        IdentityService identity = mock(IdentityService.class);
        when(identity.currentCredential(anyLong(), anyLong(), anyString(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        BearerTokenFilter filter = new BearerTokenFilter(jwt, identity);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("AUTH_STATE_UNAVAILABLE"));
        assertNull(chain.getRequest());
    }

    @Test
    void refusedMySqlConnectionReturns503WithoutPassingRequestToApplication() throws Exception {
        JwtService jwt = new JwtService("stage-4-filter-test-secret-at-least-32-bytes");
        String token = jwt.issue(new CurrentUser(43, "mysql-filter-test", "MySQL Filter Test", "ADMIN", 0, null));
        DriverManagerDataSource unavailable = new DriverManagerDataSource(
                "jdbc:mysql://127.0.0.1:1/stu_stage4_test_unavailable", "stage4-test", "");
        IdentityService identity = new IdentityService(new JdbcTemplate(unavailable), new BCryptPasswordEncoder(4),
                mock(AtomicRateLimitService.class));
        BearerTokenFilter filter = new BearerTokenFilter(jwt, identity);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("AUTH_STATE_UNAVAILABLE"));
        assertNull(chain.getRequest());
    }
}
