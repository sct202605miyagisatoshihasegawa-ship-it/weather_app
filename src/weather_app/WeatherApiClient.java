package weather_app;

/** Obtains one current-weather observation without exposing UI concerns. */
public interface WeatherApiClient {
    WeatherRecord fetch(String city) throws WeatherApiException;
}
