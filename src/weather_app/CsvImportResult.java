package weather_app;

import java.util.List;

/** Summary of a successful CSV migration. Skipped records already existed in SQLite or repeated a CSV row. */
public record CsvImportResult(int importedCount, int skippedDuplicateCount, List<WeatherRecord> importedRecords) {
    public CsvImportResult {
        importedRecords = List.copyOf(importedRecords);
    }
}
