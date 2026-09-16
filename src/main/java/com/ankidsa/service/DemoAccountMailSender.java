package com.ankidsa.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.io.IOException;
import java.util.UUID;

@Component @Profile("demo")
public class DemoAccountMailSender implements AccountMailSender {
    @Override
    public void send(String recipient, String subject, String body) {
        try {
            Path directory = Path.of(".local", "outbox");
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(UUID.randomUUID() + ".eml"),
                "To: " + recipient + "\nSubject: " + subject + "\n\n" + body,
                StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new IllegalStateException("Could not write to the local demo mailbox.", e);
        }
    }
}
