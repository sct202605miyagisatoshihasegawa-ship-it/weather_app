package weather_app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class OpenWeatherMapClientTest {
    @Test
    void fetchesObservationFromDummyJsonWithoutNetworkAccess() throws Exception {
        WeatherHttpTransport dummyApi = url -> resource("weather-success.json");

        WeatherRecord record = new OpenWeatherMapClient("dummy-key", dummyApi).fetch("Sendai,JP");

        assertEquals("Sendai,JP", record.city());
        assertEquals(23.4, record.temperature());
        assertEquals(68.0, record.humidity());
        assertEquals(1008.6, record.pressure());
    }

    @Test
    void rejectsCommunicationFailureWithoutCreatingObservation() {
        WeatherHttpTransport dummyApi = url -> { throw new IOException("simulated offline"); };

        assertThrows(WeatherApiException.class, () -> new OpenWeatherMapClient("dummy-key", dummyApi).fetch("Sendai,JP"));
    }

    @Test
    void rejectsMalformedJsonWithoutCreatingObservation() {
        WeatherHttpTransport dummyApi = url -> "{\"main\":{\"temp\":23.4,\"humidity\":68,\"pressure\":1008.6}";

        assertThrows(WeatherApiException.class, () -> new OpenWeatherMapClient("dummy-key", dummyApi).fetch("Sendai,JP"));
    }

    @Test
    void rejectsResponseMissingARequiredFieldWithoutCreatingObservation() throws Exception {
        WeatherHttpTransport dummyApi = url -> resource("weather-missing-pressure.json");

        assertThrows(WeatherApiException.class, () -> new OpenWeatherMapClient("dummy-key", dummyApi).fetch("Sendai,JP"));
    }

    private static String resource(String name) throws IOException {
        try (InputStream stream = OpenWeatherMapClientTest.class.getResourceAsStream("/weather_app/" + name)) {
            if (stream == null) {
                throw new IOException("Test resource not found: " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
