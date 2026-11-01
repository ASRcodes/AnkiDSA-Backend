package com.ankidsa;

import com.ankidsa.service.ReminderDispatchCoordinator;
import com.ankidsa.service.SchedulerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class ReminderDispatchCoordinatorTest {
    private final SchedulerService scheduler = mock(SchedulerService.class);
    private final ReminderDispatchCoordinator coordinator = new ReminderDispatchCoordinator(scheduler);

    @Test void skipsTicksBeforeApplicationIsReady() {
        coordinator.dispatch();
        verifyNoInteractions(scheduler);
    }

    @Test void readyEventDoesNotDispatchOnTheStartupThread() {
        coordinator.onApplicationReady();
        verifyNoInteractions(scheduler);
    }

    @Test void recurringTicksDelegateAfterReadiness() {
        coordinator.onApplicationReady();
        coordinator.dispatch();
        coordinator.dispatch();
        verify(scheduler, times(2)).runBatch();
    }

    @Test void failureDoesNotPreventTheNextTickOrExposeExceptionDetails(CapturedOutput output) {
        when(scheduler.runBatch()).thenThrow(new IllegalStateException("private-token-or-database-password"))
                .thenReturn(null);
        coordinator.onApplicationReady();
        assertDoesNotThrow(coordinator::dispatch);
        assertDoesNotThrow(coordinator::dispatch);
        verify(scheduler, times(2)).runBatch();
        assertThat(output).contains("Automatic reminder batch failed")
                .doesNotContain("private-token-or-database-password");
    }

    @Test void stopsStartingBatchesWhenTheContextCloses() {
        coordinator.onApplicationReady();
        coordinator.onContextClosed();
        coordinator.dispatch();
        verifyNoInteractions(scheduler);
    }

    @Test void disabledNotificationsDoNotCreateAnAutomaticDispatcher() {
        context().withPropertyValues("app.notifications-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ReminderDispatchCoordinator.class));
        context().run(context -> assertThat(context).doesNotHaveBean(ReminderDispatchCoordinator.class));
    }

    @Test void enabledNotificationsCreateTheDispatcher() {
        context().withPropertyValues("app.notifications-enabled=true")
                .run(context -> assertThat(context).hasSingleBean(ReminderDispatchCoordinator.class));
    }

    private ApplicationContextRunner context() {
        return new ApplicationContextRunner()
                .withBean(SchedulerService.class, () -> scheduler)
                .withUserConfiguration(ReminderDispatchCoordinator.class);
    }
}
