package weather_app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class WeatherGraphDataServiceTest {
    @Test
    void usesTheRequiredAggregationForPresetAndCustomPeriods() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 21, 12, 0);

        assertEquals(GraphAggregation.FIFTEEN_MINUTES, GraphPeriod.lastDay(now).aggregation());
        assertEquals(GraphAggregation.HOURLY, GraphPeriod.lastWeek(now).aggregation());
        assertEquals(GraphAggregation.DAILY, GraphPeriod.lastYear(now).aggregation());
        assertEquals(GraphAggregation.FIFTEEN_MINUTES, GraphPeriod.custom(now.minusDays(7), now).aggregation());
        assertEquals(GraphAggregation.HOURLY, GraphPeriod.custom(now.minusDays(8), now).aggregation());
        assertEquals(GraphAggregation.DAILY, GraphPeriod.custom(now.minusDays(90), now).aggregation());
    }

    @Test
    void averagesObservationsWithinEachFifteenMinuteBucket() {
        List<WeatherRecord> result = WeatherGraphDataService.aggregate(List.of(
                record("2026-07-20T09:01", 20.0), record("2026-07-20T09:14", 24.0), record("2026-07-20T09:15", 30.0)),
                GraphAggregation.FIFTEEN_MINUTES);

        assertEquals(2, result.size());
        assertEquals(LocalDateTime.parse("2026-07-20T09:00"), result.get(0).dateTime());
        assertEquals(22.0, result.get(0).temperature());
        assertEquals(LocalDateTime.parse("2026-07-20T09:15"), result.get(1).dateTime());
    }

    @Test
    void averagesHourlyAndDailyBuckets() {
        List<WeatherRecord> records = List.of(record("2026-07-20T09:01", 20.0), record("2026-07-20T09:45", 24.0),
                record("2026-07-20T10:01", 30.0));

        assertEquals(2, WeatherGraphDataService.aggregate(records, GraphAggregation.HOURLY).size());
        List<WeatherRecord> daily = WeatherGraphDataService.aggregate(records, GraphAggregation.DAILY);
        assertEquals(1, daily.size());
        assertEquals(24.666666666666668, daily.getFirst().temperature());
    }

    private static WeatherRecord record(String timestamp, double temperature) {
        return new WeatherRecord(LocalDateTime.parse(timestamp), "Sendai,JP", temperature, 60.0, 1000.0);
    }
}
