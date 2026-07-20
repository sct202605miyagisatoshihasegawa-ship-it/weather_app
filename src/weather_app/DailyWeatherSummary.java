package weather_app;

import java.time.LocalDate;

/** Daily averages returned by the persistence layer. */
public record DailyWeatherSummary(LocalDate date, String city, double averageTemperature,
        double averageHumidity, double averagePressure, int observationCount) {
}
