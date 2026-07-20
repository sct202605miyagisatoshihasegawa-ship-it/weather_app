package weather_app;

import java.io.IOException;

/** Small HTTP boundary so API tests can use a dummy response instead of the network. */
@FunctionalInterface
interface WeatherHttpTransport {
    String get(String url) throws IOException;
}
