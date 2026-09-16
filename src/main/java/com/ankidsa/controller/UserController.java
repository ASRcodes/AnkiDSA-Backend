package com.ankidsa.controller;
import com.ankidsa.config.AuthUtil;
import com.ankidsa.service.ReminderDeviceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/users") @RequiredArgsConstructor
public class UserController {
    private final ReminderDeviceService devices;
    public record Device(@NotBlank @Size(max = 4096) String token) {}

    @PutMapping("/me/device") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registerDevice(@Valid @RequestBody Device device) {
        devices.register(AuthUtil.getCurrentUserId(), AuthUtil.getCurrentSessionVersion(), device.token());
    }

    @DeleteMapping("/me/device") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeDevice(@Valid @RequestBody Device device) {
        devices.remove(AuthUtil.getCurrentUserId(), device.token());
    }
}
