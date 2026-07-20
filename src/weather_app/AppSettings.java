package weather_app;

/** User-configurable values stored outside the source tree. */
public record AppSettings(String apiKey, int collectionIntervalMinutes) {
    public static final int DEFAULT_COLLECTION_INTERVAL_MINUTES = 15;

    public AppSettings {
        if (apiKey == null) {
            apiKey = "";
        }
        if (collectionIntervalMinutes <= 0) {
            throw new IllegalArgumentException("Collection interval must be positive");
        }
    }

    public static AppSettings defaults() {
        return new AppSettings("", DEFAULT_COLLECTION_INTERVAL_MINUTES);
    }
}
