package com.ankidsa.controller;
import com.ankidsa.config.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
@RestController @Profile("demo") @RequiredArgsConstructor
public class DemoController {
    private final DemoData data;
    @PostMapping("/api/demo/reset") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset() { data.reset(AuthUtil.getCurrentUserId()); }
}
