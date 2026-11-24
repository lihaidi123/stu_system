package edu.stusystem.service;

import edu.stusystem.api.ApiException;
import edu.stusystem.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SensitivePasswordVerificationServiceTest {
    private static final CurrentUser USER = new CurrentUser(17,"mira","Mira","TEACHER",0,null);
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AtomicRateLimitService limiter = mock(AtomicRateLimitService.class);
    private final SensitivePasswordVerificationService service = new SensitivePasswordVerificationService(db,encoder,limiter);

    @Test
    void wrongPasswordUsesAccountScopedThreeFailureFiveMinuteLock() {
        when(db.queryForObject(anyString(),eq(String.class),eq(17L))).thenReturn("encoded");
        when(encoder.matches("WrongPass!234","encoded")).thenReturn(false);

        ApiException error=assertThrows(ApiException.class,()->service.verify(USER,"WrongPass!234"));

        assertEquals("CURRENT_PASSWORD_INVALID",error.code());
        verify(limiter).recordFailure("auth:sensitive:account:17:failures","auth:sensitive:account:17:locked",3,Duration.ofMinutes(5),Duration.ofMinutes(5));
    }

    @Test
    void lockedAccountDoesNotReadOrComparePassword() {
        when(limiter.isLimited("auth:sensitive:account:17:locked")).thenReturn(true);

        ApiException error=assertThrows(ApiException.class,()->service.verify(USER,"WrongPass!234"));

        assertEquals("SENSITIVE_VERIFICATION_LOCKED",error.code());
        verifyNoInteractions(db,encoder);
    }

    @Test
    void validPasswordClearsItsFailureAndLockKeys() {
        when(db.queryForObject(anyString(),eq(String.class),eq(17L))).thenReturn("encoded");
        when(encoder.matches("RightPass!234","encoded")).thenReturn(true);

        service.verify(USER,"RightPass!234");

        verify(limiter).clear("auth:sensitive:account:17:failures","auth:sensitive:account:17:locked");
        verify(limiter,never()).recordFailure(anyString(),anyString(),any(Long.class),any(Duration.class),any(Duration.class));
    }
}
