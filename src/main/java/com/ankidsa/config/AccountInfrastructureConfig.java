package com.ankidsa.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.time.*;

@Configuration(proxyBeanMethods = false)
public class AccountInfrastructureConfig {
    @Bean
    Clock appClock(@Value("${app.timezone}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }

    @Bean
    ThreadPoolTaskExecutor accountMailExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("account-mail-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
