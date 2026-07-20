package weather_app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class ScheduledAlertNotifierTest {
    @Test
    void startsNotificationForDummyAlertAndStopsWhenRequested() throws Exception {
        List<String> messages = new ArrayList<>();
        CountDownLatch soundPlayed = new CountDownLatch(1);
        try (ScheduledAlertNotifier notifier = new ScheduledAlertNotifier(messages::add, soundPlayed::countDown,
                Executors.newSingleThreadScheduledExecutor(), Duration.ofMillis(20), Duration.ofSeconds(1))) {
            WeatherRecord current = new WeatherRecord(LocalDateTime.of(2026, 7, 20, 12, 0), "Sendai,JP", 20.0, 60.0, 1003.0);
            WeatherRecord comparison = new WeatherRecord(current.dateTime().minusHours(1), "Sendai,JP", 20.0, 60.0, 1000.0);

            notifier.notifyAlert(new PressureAlert(current, comparison, 3.0));

            assertTrue(soundPlayed.await(1, TimeUnit.SECONDS));
            assertTrue(notifier.isActive());
            assertTrue(messages.getFirst().contains("急上昇"));

            notifier.notifyAlert(new PressureAlert(current, comparison, 3.0));
            assertEquals(1, messages.size());

            notifier.stop();

            assertFalse(notifier.isActive());
        }
    }
}
