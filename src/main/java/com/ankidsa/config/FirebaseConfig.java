package com.ankidsa.config;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.*;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import java.io.IOException;
@Configuration
@ConditionalOnProperty(name="app.notifications-enabled",havingValue="true")
public class FirebaseConfig {
    @PostConstruct public void initialize() throws IOException {
        if(FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(GoogleCredentials.getApplicationDefault()).build());
        }
    }
}
