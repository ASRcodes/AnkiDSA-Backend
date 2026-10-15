package com.ankidsa.service;

public interface AccountMailSender {
    default boolean available() { return true; }
    void send(String recipient, String subject, String body);
}
