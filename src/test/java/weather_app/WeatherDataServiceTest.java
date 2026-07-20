package weather_app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WeatherDataServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void collectsPersistsAndPublishesOnlySuccessfulObservations() throws Exception {
        WeatherRecord sendai = new WeatherRecord(LocalDateTime.of(2026, 7, 20, 12, 0), "Sendai,JP", 25.0, 60.0, 1005.0);
        WeatherApiClient dummyApi = city -> {
            if (city.equals("Tokyo,JP")) {
                throw new WeatherApiException("simulated API failure");
            }
            return sendai;
        };
        List<WeatherRecord> published = new ArrayList<>();
        List<String> failedCities = new ArrayList<>();

        try (WeatherRepository repository = new WeatherRepository(temporaryDirectory.resolve("weather-test.db"));
                WeatherDataService service = new WeatherDataService(List.of("Sendai,JP", "Tokyo,JP"), dummyApi, repository,
                        published::add, (city, error) -> failedCities.add(city))) {
            service.collectNow();

            assertEquals(1, published.size());
            assertEquals(sendai, published.get(0));
            assertEquals(List.of("Tokyo,JP"), failedCities);
            List<WeatherRecord> persisted = repository.find(LocalDateTime.of(2026, 7, 20, 0, 0),
                    LocalDateTime.of(2026, 7, 21, 0, 0), "Sendai,JP");
            assertEquals(1, persisted.size());
            assertEquals(1005.0, persisted.get(0).pressure());
        }
    }

    @Test
    void rejectsANonPositiveCollectionInterval() throws Exception {
        WeatherApiClient dummyApi = city -> { throw new WeatherApiException("not used"); };
        try (WeatherRepository repository = new WeatherRepository(temporaryDirectory.resolve("weather-test.db"));
                WeatherDataService service = new WeatherDataService(List.of("Sendai,JP"), dummyApi, repository,
                        record -> { }, (city, error) -> { })) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> service.start(java.time.Duration.ZERO));
        }
    }
}
