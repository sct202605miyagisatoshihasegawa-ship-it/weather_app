package weather_app;

import java.sql.SQLException;
import java.time.Duration;
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
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public WeatherDataService(List<String> cities, WeatherApiClient apiClient, WeatherRepository repository,
            Consumer<WeatherRecord> recordConsumer, BiConsumer<String, Exception> failureConsumer) {
        this.cities = List.copyOf(cities);
        this.apiClient = apiClient;
        this.repository = repository;
        this.recordConsumer = recordConsumer;
        this.failureConsumer = failureConsumer;
    }

    public void start(Duration interval) {
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("Collection interval must be positive");
        }
        scheduler.scheduleAtFixedRate(this::collectNow, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void collectNow() {
        for (String city : cities) {
            try {
                WeatherRecord record = apiClient.fetch(city);
                repository.save(record);
                recordConsumer.accept(record);
            } catch (WeatherApiException | SQLException e) {
                failureConsumer.accept(city, e);
            }
        }
    }

    @Override
    public void close() throws SQLException {
        scheduler.shutdownNow();
        repository.close();
    }
}
