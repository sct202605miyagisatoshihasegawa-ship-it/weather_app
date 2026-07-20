package weather_app;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Validates the complete legacy or quoted CSV file before any database write is attempted. */
public final class WeatherCsvImporter {
    public List<WeatherRecord> readAll(Path csvFile) throws IOException, WeatherCsvFormatException {
        List<WeatherRecord> records = new ArrayList<>();
        int invalidRows = 0;
        String firstError = null;
        try (BufferedReader reader = Files.newBufferedReader(csvFile, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank() || isHeader(line)) {
                    continue;
                }
                try {
                    records.add(parseRecord(line));
                } catch (IllegalArgumentException e) {
                    invalidRows++;
                    if (firstError == null) {
                        firstError = lineNumber + "行目: " + e.getMessage();
                    }
                }
            }
        }
        if (invalidRows > 0) {
            throw new WeatherCsvFormatException(invalidRows, firstError);
        }
        return records;
    }

    private static boolean isHeader(String line) {
        return line.strip().toLowerCase().startsWith("observed_at");
    }

    private static WeatherRecord parseRecord(String line) {
        List<String> values = parseValues(line);
        String city;
        int metricOffset;
        if (values.size() == 5) {
            city = values.get(1);
            metricOffset = 2;
        } else if (values.size() == 6) {
            city = values.get(1) + "," + values.get(2);
            metricOffset = 3;
        } else {
            throw new IllegalArgumentException("列数が5列または6列ではありません");
        }
        if (city.isBlank()) {
            throw new IllegalArgumentException("都市名が空です");
        }
        try {
            LocalDateTime dateTime = LocalDateTime.parse(values.get(0));
            double temperature = finiteNumber(values.get(metricOffset), "気温");
            double humidity = finiteNumber(values.get(metricOffset + 1), "湿度");
            double pressure = finiteNumber(values.get(metricOffset + 2), "気圧");
            return new WeatherRecord(dateTime, city, temperature, humidity, pressure);
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("観測日時の形式が不正です");
        }
    }

    private static double finiteNumber(String value, String label) {
        try {
            double number = Double.parseDouble(value);
            if (!Double.isFinite(number)) {
                throw new NumberFormatException();
            }
            return number;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + "が数値ではありません");
        }
    }

    private static List<String> parseValues(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                values.add(value.toString().trim());
                value.setLength(0);
            } else {
                value.append(character);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("引用符が閉じられていません");
        }
        values.add(value.toString().trim());
        return values;
    }
}
