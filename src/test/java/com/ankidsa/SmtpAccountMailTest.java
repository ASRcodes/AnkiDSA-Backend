package com.ankidsa;

import com.ankidsa.service.SmtpAccountMailSender;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SmtpAccountMailTest {
    @Test void recoveryMailIsDeliveredToALocalSmtpServer() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                var executor = Executors.newSingleThreadExecutor()) {
            server.setSoTimeout(5000);
            var received = executor.submit(() -> {
                try (var socket = server.accept();
                        var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                        var output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
                    socket.setSoTimeout(5000);
                    output.print("220 localhost test SMTP\r\n"); output.flush();
                    var body = new StringBuilder();
                    boolean data = false;
                    String line;
                    while ((line = input.readLine()) != null) {
                        if (data) {
                            if (line.equals(".")) {
                                data = false;
                                output.print("250 accepted\r\n");
                            } else { body.append(line).append('\n'); }
                        } else if (line.startsWith("DATA")) {
                            data = true;
                            output.print("354 send data\r\n");
                        } else if (line.startsWith("QUIT")) {
                            output.print("221 goodbye\r\n"); output.flush();
                            return body.toString();
                        } else {
                            output.print("250 localhost\r\n");
                        }
                        output.flush();
                    }
                    return body.toString();
                }
            });
            var transport = new JavaMailSenderImpl();
            transport.setHost(server.getInetAddress().getHostAddress());
            transport.setPort(server.getLocalPort());
            transport.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "5000");
            transport.getJavaMailProperties().setProperty("mail.smtp.timeout", "5000");
            var sender = new SmtpAccountMailSender(transport, transport.getHost(), "accounts@ankidsa.invalid");
            sender.send("test@ankidsa.invalid", "Reset your AnkiDSA password", "Local SMTP transport check.");
            String message = received.get(5, TimeUnit.SECONDS);
            assertTrue(message.contains("To: test@ankidsa.invalid"));
            assertTrue(message.contains("From: accounts@ankidsa.invalid"));
            assertTrue(message.contains("Subject: Reset your AnkiDSA password"));
            assertTrue(message.contains("Local SMTP transport check."));
        }
    }
}
