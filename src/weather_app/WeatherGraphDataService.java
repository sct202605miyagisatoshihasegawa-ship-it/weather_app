package weather_app;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Reads only a selected period and averages observations into the chart's requested resolution. */
public final class WeatherGraphDataService {
    public List<WeatherRecord> load(WeatherRepository repository, List<String> cities, GraphPeriod period) throws SQLException {
        List<WeatherRecord> result = new ArrayList<>();
        for (String city : cities) {
            result.addAll(aggregate(repository.find(period.fromInclusive(), period.toExclusive(), city), period.aggregation()));
        }
        return result;
    }

    static List<WeatherRecord> aggregate(List<WeatherRecord> records, GraphAggregation aggregation) {
        Map<LocalDateTime, Averages> buckets = new TreeMap<>();
        for (WeatherRecord record : records) {
            LocalDateTime bucket = bucketStart(record.dateTime(), aggregation);
            buckets.computeIfAbsent(bucket, ignored -> new Averages(record.city())).add(record);
        }
        List<WeatherRecord> aggregated = new ArrayList<>();
        for (Map.Entry<LocalDateTime, Averages> entry : buckets.entrySet()) {
            aggregated.add(entry.getValue().toRecord(entry.getKey()));
        }
        return aggregated;
    }

    private static LocalDateTime bucketStart(LocalDateTime dateTime, GraphAggregation aggregation) {
        return switch (aggregation) {
        case FIFTEEN_MINUTES -> dateTime.withMinute((dateTime.getMinute() / 15) * 15).withSecond(0).withNano(0);
        case HOURLY -> dateTime.withMinute(0).withSecond(0).withNano(0);
        case DAILY -> dateTime.toLocalDate().atStartOfDay();
        };
    }

    private static final class Averages {
        private final String city;
        private double temperatureTotal;
        private double humidityTotal;
        private double pressureTotal;
        private int count;

        private Averages(String city) {
            this.city = city;
        }

        private void add(WeatherRecord record) {
            temperatureTotal += record.temperature();
            humidityTotal += record.humidity();
            pressureTotal += record.pressure();
            count++;
        }

        private WeatherRecord toRecord(LocalDateTime bucket) {
            return new WeatherRecord(bucket, city, temperatureTotal / count, humidityTotal / count, pressureTotal / count);
        }
    }
}
