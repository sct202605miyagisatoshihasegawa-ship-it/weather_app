package weather_app;

import java.time.LocalDateTime;

/** Snapshot of one scheduled weather collection cycle for the UI status area. */
public record WeatherCollectionStatus(State state, LocalDateTime timestamp, int consecutiveFailures, String detail) {
    public enum State {
        FETCHING,
        SUCCESS,
        FAILURE
    }
}
