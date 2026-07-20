package weather_app;

/** Raised before SQLite is changed when a CSV contains one or more invalid data rows. */
public final class WeatherCsvFormatException extends Exception {
    private final int invalidRowCount;

    public WeatherCsvFormatException(int invalidRowCount, String firstError) {
        super(firstError);
        this.invalidRowCount = invalidRowCount;
    }

    public int invalidRowCount() {
        return invalidRowCount;
    }
}
