package weather_app;

import java.time.Duration;
import java.time.LocalDateTime;

/** An inclusive-start, exclusive-end graph range and its required aggregation resolution. */
public record GraphPeriod(LocalDateTime fromInclusive, LocalDateTime toExclusive, GraphAggregation aggregation) {
    public GraphPeriod {
        if (!toExclusive.isAfter(fromInclusive)) {
            throw new IllegalArgumentException("終了日時は開始日時より後にしてください");
        }
    }

    public static GraphPeriod lastDay(LocalDateTime now) {
        return new GraphPeriod(now.minusDays(1), now, GraphAggregation.FIFTEEN_MINUTES);
    }

    public static GraphPeriod lastWeek(LocalDateTime now) {
        return new GraphPeriod(now.minusWeeks(1), now, GraphAggregation.HOURLY);
    }

    public static GraphPeriod lastYear(LocalDateTime now) {
        return new GraphPeriod(now.minusYears(1), now, GraphAggregation.DAILY);
    }

    public static GraphPeriod custom(LocalDateTime fromInclusive, LocalDateTime toExclusive) {
        Duration duration = Duration.between(fromInclusive, toExclusive);
        GraphAggregation aggregation = duration.toDays() >= 90 ? GraphAggregation.DAILY
                : duration.toDays() >= 8 ? GraphAggregation.HOURLY : GraphAggregation.FIFTEEN_MINUTES;
        return new GraphPeriod(fromInclusive, toExclusive, aggregation);
    }

    public boolean contains(LocalDateTime dateTime) {
        return !dateTime.isBefore(fromInclusive) && dateTime.isBefore(toExclusive);
    }
}
