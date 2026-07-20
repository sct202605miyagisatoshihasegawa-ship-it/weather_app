package weather_app;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Determines whether an observation requires a Sendai pressure alert. */
public final class PressureAlertService {
    static final String TARGET_CITY = "Sendai,JP";
    private static final double THRESHOLD_HPA = 3.0;
    private static final long MINIMUM_AGE_MINUTES = 45;
    private static final long MAXIMUM_AGE_MINUTES = 75;
    private static final long TARGET_AGE_MINUTES = 60;

    public Optional<PressureAlert> evaluate(WeatherRecord current, List<WeatherRecord> history) {
        if (!TARGET_CITY.equals(current.city())) {
            return Optional.empty();
        }

        Optional<WeatherRecord> comparison = history.stream()
                .filter(record -> TARGET_CITY.equals(record.city()))
                .filter(record -> isCandidate(record, current))
                .min(Comparator
                        .comparingLong((WeatherRecord record) -> distanceFromOneHour(record, current))
                        .thenComparing(WeatherRecord::dateTime));

        if (comparison.isEmpty()) {
            return Optional.empty();
        }

        double difference = current.pressure() - comparison.get().pressure();
        if (Math.abs(difference) < THRESHOLD_HPA) {
            return Optional.empty();
        }
        return Optional.of(new PressureAlert(current, comparison.get(), difference));
    }

    private boolean isCandidate(WeatherRecord record, WeatherRecord current) {
        long age = Duration.between(record.dateTime(), current.dateTime()).toMinutes();
        return age >= MINIMUM_AGE_MINUTES && age <= MAXIMUM_AGE_MINUTES;
    }

    private long distanceFromOneHour(WeatherRecord record, WeatherRecord current) {
        long age = Duration.between(record.dateTime(), current.dateTime()).toMinutes();
        return Math.abs(TARGET_AGE_MINUTES - age);
    }
}
