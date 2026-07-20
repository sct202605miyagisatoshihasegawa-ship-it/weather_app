package weather_app;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Repeats an alert sound without blocking the Swing event thread. */
public final class ScheduledAlertNotifier implements AlertNotifier {
    private static final Duration REPEAT_INTERVAL = Duration.ofSeconds(10);
    private static final Duration MAXIMUM_DURATION = Duration.ofMinutes(3);

    private final Consumer<String> messageConsumer;
    private final Runnable soundPlayer;
    private final ScheduledExecutorService scheduler;
    private final Duration repeatInterval;
    private final Duration maximumDuration;
    private ScheduledFuture<?> repeatFuture;
    private ScheduledFuture<?> stopFuture;
    private boolean active;

    public ScheduledAlertNotifier(Consumer<String> messageConsumer, Runnable soundPlayer) {
        this(messageConsumer, soundPlayer, Executors.newSingleThreadScheduledExecutor(), REPEAT_INTERVAL, MAXIMUM_DURATION);
    }

    ScheduledAlertNotifier(Consumer<String> messageConsumer, Runnable soundPlayer, ScheduledExecutorService scheduler,
            Duration repeatInterval, Duration maximumDuration) {
        this.messageConsumer = messageConsumer;
        this.soundPlayer = soundPlayer;
        this.scheduler = scheduler;
        this.repeatInterval = repeatInterval;
        this.maximumDuration = maximumDuration;
    }

    @Override
    public synchronized void notifyAlert(PressureAlert alert) {
        if (active) {
            return;
        }
        messageConsumer.accept(messageFor(alert));
        active = true;
        repeatFuture = scheduler.scheduleAtFixedRate(soundPlayer, 0, repeatInterval.toMillis(), TimeUnit.MILLISECONDS);
        stopFuture = scheduler.schedule(this::stop, maximumDuration.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void stop() {
        stopScheduledPlayback();
        active = false;
    }

    synchronized boolean isActive() {
        return active;
    }

    @Override
    public synchronized void close() {
        stop();
        scheduler.shutdownNow();
    }

    private void stopScheduledPlayback() {
        if (repeatFuture != null) {
            repeatFuture.cancel(false);
            repeatFuture = null;
        }
        if (stopFuture != null) {
            stopFuture.cancel(false);
            stopFuture = null;
        }
    }

    private static String messageFor(PressureAlert alert) {
        String direction = alert.isRising() ? "急上昇" : "急降下";
        return "⚠️ 仙台の気圧" + direction + "！(" + String.format("%.1f", alert.pressureDifference()) + " hPa)";
    }
}
