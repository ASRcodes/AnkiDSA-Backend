package com.ankidsa.service;
import com.ankidsa.entity.ReminderDevice;
import com.ankidsa.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;

@Service
public class ReminderDeviceService {
    private final UserRepository users;
    private final ReminderDeviceRepository devices;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public ReminderDeviceService(UserRepository users, ReminderDeviceRepository devices,
            PlatformTransactionManager manager, Clock clock) {
        this.users = users;
        this.devices = devices;
        this.transactions = new TransactionTemplate(manager);
        this.clock = clock;
    }

    public void register(long userId, long sessionVersion, String token) {
        // A unique fingerprint and transaction retry keep one current owner,
        // even when two sessions first register a device at the same time.
        try {
            registerOnce(userId, sessionVersion, token);
        } catch (DataIntegrityViolationException collision) {
            registerOnce(userId, sessionVersion, token);
        }
    }

    private void registerOnce(long userId, long sessionVersion, String token) {
        transactions.executeWithoutResult(status -> {
            var user = users.lockById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
            if (user.getTokenVersion() != sessionVersion)
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            String hash = PasswordRecoveryService.fingerprint(token);
            var device = devices.lockByHash(hash).orElseGet(() -> ReminderDevice.builder().tokenHash(hash).build());
            device.setUser(user);
            device.setToken(token);
            device.setSessionVersion(sessionVersion);
            device.setUpdatedAt(clock.instant());
            devices.saveAndFlush(device);
        });
    }

    @Transactional
    public void remove(long userId, String token) {
        devices.removeOwned(userId, PasswordRecoveryService.fingerprint(token));
    }
}
