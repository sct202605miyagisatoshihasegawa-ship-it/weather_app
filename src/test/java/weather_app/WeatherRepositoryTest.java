package weather_app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WeatherRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void savesSearchesAndAggregatesOnlyInTemporarySqlite() throws Exception {
        Path testDatabase = temporaryDirectory.resolve("weather-test.db");
        try (WeatherRepository repository = new WeatherRepository(testDatabase)) {
            repository.save(record("2026-07-20T09:00", "Sendai,JP", 20.0, 60.0, 1000.0));
            repository.save(record("2026-07-20T12:00", "Sendai,JP", 24.0, 70.0, 1004.0));
            repository.save(record("2026-07-21T09:00", "Sendai,JP", 22.0, 65.0, 1002.0));
            repository.save(record("2026-07-20T09:00", "Tokyo,JP", 30.0, 50.0, 1008.0));

            List<WeatherRecord> records = repository.find(
                    LocalDateTime.parse("2026-07-20T00:00"), LocalDateTime.parse("2026-07-21T00:00"), "Sendai,JP");
            assertEquals(2, records.size());
            assertEquals(20.0, records.get(0).temperature());

            List<DailyWeatherSummary> summaries = repository.findDailySummaries(
                    LocalDate.parse("2026-07-20"), LocalDate.parse("2026-07-21"), "Sendai,JP");
            assertEquals(2, summaries.size());
            assertEquals(22.0, summaries.get(0).averageTemperature());
            assertEquals(65.0, summaries.get(0).averageHumidity());
            assertEquals(1002.0, summaries.get(0).averagePressure());
            assertEquals(2, summaries.get(0).observationCount());
        }
    }

    @Test
    void importsOnlyMissingRowsAndKeepsExistingObservations() throws Exception {
        Path testDatabase = temporaryDirectory.resolve("weather-import-test.db");
        try (WeatherRepository repository = new WeatherRepository(testDatabase)) {
            WeatherRecord existing = record("2026-07-20T09:00", "Sendai,JP", 20.0, 60.0, 1000.0);
            WeatherRecord newRecord = record("2026-07-20T12:00", "Sendai,JP", 24.0, 70.0, 1004.0);
            repository.save(existing);

            CsvImportResult result = repository.importIfAbsent(List.of(existing, newRecord, newRecord));

            assertEquals(1, result.importedCount());
            assertEquals(2, result.skippedDuplicateCount());
            assertEquals(List.of(newRecord), result.importedRecords());
            List<WeatherRecord> stored = repository.find(
                    LocalDateTime.parse("2026-07-20T00:00"), LocalDateTime.parse("2026-07-21T00:00"), "Sendai,JP");
            assertEquals(2, stored.size());
            assertEquals(20.0, stored.get(0).temperature());
        }
    }

    @Test
    void createsAnIndependentSqliteBackup() throws Exception {
        Path testDatabase = temporaryDirectory.resolve("weather-backup-test.db");
        Path backupFile = temporaryDirectory.resolve("backups").resolve("before-import.db");
        java.nio.file.Files.createDirectories(backupFile.getParent());
        try (WeatherRepository repository = new WeatherRepository(testDatabase)) {
            repository.save(record("2026-07-20T09:00", "Sendai,JP", 20.0, 60.0, 1000.0));
            repository.backupTo(backupFile);
        }

        try (WeatherRepository backupRepository = new WeatherRepository(backupFile)) {
            assertEquals(1, backupRepository.findAll().size());
        }
    }

    private static WeatherRecord record(String timestamp, String city, double temperature, double humidity, double pressure) {
        return new WeatherRecord(LocalDateTime.parse(timestamp), city, temperature, humidity, pressure);
    }
}
