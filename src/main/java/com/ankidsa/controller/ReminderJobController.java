package com.ankidsa.controller;
import com.ankidsa.service.SchedulerService;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReminderJobController {
    private final SchedulerService reminders;
    public ReminderJobController(SchedulerService reminders) { this.reminders = reminders; }
    @PostMapping("/internal/reminders") public SchedulerService.Result run() { return reminders.runBatch(); }
}
