package weather_app;

/** Indicates that an API response could not be used as an observation. */
public class WeatherApiException extends Exception {
    public WeatherApiException(String message) {
        super(message);
    }

    public WeatherApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
