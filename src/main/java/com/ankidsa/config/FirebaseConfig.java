package com.ankidsa.config;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.*;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import java.io.IOException;
@Configuration
@ConditionalOnProperty(name="app.notifications-enabled",havingValue="true")
public class FirebaseConfig {
    @Bean public FirebaseMessaging firebaseMessaging(@Value("${FIREBASE_PROJECT_ID:}") String projectId) throws IOException {
        var options = FirebaseOptions.builder().setCredentials(GoogleCredentials.getApplicationDefault())
            .setConnectTimeout(5000).setReadTimeout(10000).setWriteTimeout(10000);
        if (!projectId.isBlank()) options.setProjectId(projectId);
        FirebaseApp app = FirebaseApp.getApps().stream().filter(a -> a.getName().equals("ankidsa"))
            .findFirst().orElseGet(() -> FirebaseApp.initializeApp(options.build(), "ankidsa"));
        return FirebaseMessaging.getInstance(app);
    }
}
