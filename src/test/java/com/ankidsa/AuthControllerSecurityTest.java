package com.ankidsa;

import com.ankidsa.config.JwtService;
import com.ankidsa.controller.AuthController;
import com.ankidsa.entity.User;
import com.ankidsa.repository.UserRepository;
import com.ankidsa.service.ReminderDeviceService;
import com.ankidsa.service.SessionRevocations;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuthControllerSecurityTest {
    @Test void unknownAndPasswordlessAccountsStillPerformValidCostTwelveChecks() {
        var users = mock(UserRepository.class);
        when(users.findByEmail("missing@example.test")).thenReturn(Optional.empty());
        when(users.findByEmail("legacy@example.test")).thenReturn(Optional.of(
            User.builder().id(42L).email("legacy@example.test").passwordHash(null).build()));
        var passwords = spy(new BCryptPasswordEncoder(12));
        var jwt = mock(JwtService.class);
        var controller = new AuthController(users, jwt, passwords,
            mock(SessionRevocations.class), mock(ReminderDeviceService.class));
        String attemptedPassword = "UnrelatedAttempt!2026";

        for (String email : new String[] {"missing@example.test", "legacy@example.test"}) {
            var error = assertThrows(ResponseStatusException.class, () ->
                controller.login(new AuthController.Credentials(email, attemptedPassword, null)));
            assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
            assertEquals("Email or password is incorrect.", error.getReason());
        }

        var checkedHashes = ArgumentCaptor.forClass(String.class);
        verify(passwords, times(2)).matches(eq(attemptedPassword), checkedHashes.capture());
        for (String hash : checkedHashes.getAllValues()) {
            // A malformed hash can return quickly; require a valid hash at the real-account cost.
            assertTrue(hash.matches("\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}"));
            assertFalse(BCrypt.checkpw(attemptedPassword, hash));
        }
        verifyNoInteractions(jwt);
    }
}
