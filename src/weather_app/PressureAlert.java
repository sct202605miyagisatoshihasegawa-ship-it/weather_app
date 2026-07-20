package weather_app;

/** A pressure change that meets the fixed Sendai alert threshold. */
public record PressureAlert(WeatherRecord currentRecord, WeatherRecord comparisonRecord, double pressureDifference) {
    public boolean isRising() {
        return pressureDifference > 0;
    }
}
