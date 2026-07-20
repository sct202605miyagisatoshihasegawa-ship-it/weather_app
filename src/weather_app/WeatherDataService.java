package weather_app;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Fetches, persists, and publishes weather observations outside the Swing event thread. */
public final class WeatherDataService implements AutoCloseable {
    private final List<String> cities;
    private final WeatherApiClient apiClient;
    private final WeatherRepository repository;
    private final Consumer<WeatherRecord> recordConsumer;
    private final BiConsumer<String, Exception> failureConsumer;
    private final Consumer<WeatherCollectionStatus> statusConsumer;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private int consecutiveFailures;

    public WeatherDataService(List<String> cities, WeatherApiClient apiClient, WeatherRepository repository,
            Consumer<WeatherRecord> recordConsumer, BiConsumer<String, Exception> failureConsumer,
            Consumer<WeatherCollectionStatus> statusConsumer) {
        this.cities = List.copyOf(cities);
        this.apiClient = apiClient;
        this.repository = repository;
        this.recordConsumer = recordConsumer;
        this.failureConsumer = failureConsumer;
        this.statusConsumer = statusConsumer;
    }

    public void start(Duration interval) {
        start(interval, Duration.ZERO);
    }

    public void start(Duration interval, Duration initialDelay) {
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("Collection interval must be positive");
        }
        if (initialDelay.isNegative()) {
            throw new IllegalArgumentException("Initial delay must not be negative");
        }
        scheduler.scheduleAtFixedRate(this::collectNow, initialDelay.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void collectNow() {
        statusConsumer.accept(new WeatherCollectionStatus(WeatherCollectionStatus.State.FETCHING,
                LocalDateTime.now(), consecutiveFailures, "取得中"));
        List<String> failedCities = new ArrayList<>();
        for (String city : cities) {
            try {
                WeatherRecord record = apiClient.fetch(city);
                repository.save(record);
                recordConsumer.accept(record);
            } catch (WeatherApiException | SQLException e) {
                failedCities.add(city);
                failureConsumer.accept(city, e);
            }
        }
        if (failedCities.isEmpty()) {
            consecutiveFailures = 0;
            statusConsumer.accept(new WeatherCollectionStatus(WeatherCollectionStatus.State.SUCCESS,
                    LocalDateTime.now(), consecutiveFailures, "取得成功"));
        } else {
            consecutiveFailures++;
            statusConsumer.accept(new WeatherCollectionStatus(WeatherCollectionStatus.State.FAILURE,
                    LocalDateTime.now(), consecutiveFailures, "取得失敗: " + String.join(", ", failedCities)));
        }
    }

    @Override
    public void close() throws SQLException {
        scheduler.shutdownNow();
        repository.close();
    }
}
