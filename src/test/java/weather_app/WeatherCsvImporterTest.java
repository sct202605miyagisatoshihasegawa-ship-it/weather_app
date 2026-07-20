package weather_app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WeatherCsvImporterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsTheLegacySixColumnFormatIncludingCitiesWithSpaces() throws Exception {
        Path csv = temporaryDirectory.resolve("legacy.csv");
        Files.writeString(csv, "2026-06-04T23:55:16.520563700,New York,US,27.0,35.0,1021.0\n");

        List<WeatherRecord> records = new WeatherCsvImporter().readAll(csv);

        assertEquals(1, records.size());
        assertEquals("New York,US", records.get(0).city());
        assertEquals(27.0, records.get(0).temperature());
    }

    @Test
    void readsQuotedFiveColumnFormatAndSkipsHeader() throws Exception {
        Path csv = temporaryDirectory.resolve("quoted.csv");
        Files.writeString(csv, "observed_at,city,temperature,humidity,pressure\n"
                + "2026-06-04T23:55:16.520563700,\"Sendai,JP\",14.27,71.0,1011.0\n");

        List<WeatherRecord> records = new WeatherCsvImporter().readAll(csv);

        assertEquals(1, records.size());
        assertEquals("Sendai,JP", records.get(0).city());
    }

    @Test
    void rejectsTheEntireFileWhenAnyDataRowIsInvalid() throws Exception {
        Path csv = temporaryDirectory.resolve("invalid.csv");
        Files.writeString(csv, "2026-06-04T23:55:16,Sendai,JP,14.27,71.0,1011.0\n"
                + "not-a-date,Tokyo,JP,19.43,72.0,1009.0\n");

        WeatherCsvFormatException error = assertThrows(WeatherCsvFormatException.class,
                () -> new WeatherCsvImporter().readAll(csv));

        assertEquals(1, error.invalidRowCount());
    }
}
