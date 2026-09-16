package com.ankidsa.service;

public interface AccountMailSender {
    void send(String recipient, String subject, String body);
}
