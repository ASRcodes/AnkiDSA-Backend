package com.ankidsa.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component @Profile("!demo")
@ConditionalOnProperty(name = "app.recovery.enabled", havingValue = "true")
public class SmtpAccountMailSender implements AccountMailSender {
    private final JavaMailSender sender;
    private final String from;

    public SmtpAccountMailSender(JavaMailSender sender, @Value("${spring.mail.host:}") String host,
            @Value("${app.recovery.from:}") String from) {
        if (host.isBlank() || from.isBlank())
            throw new IllegalStateException("Account recovery requires MAIL_HOST and MAIL_FROM.");
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(String recipient, String subject, String body) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }
}
