package weather_app;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** OpenWeatherMap implementation. Tests supply a dummy {@link WeatherHttpTransport}. */
public final class OpenWeatherMapClient implements WeatherApiClient {
    private static final String ENDPOINT = "https://api.openweathermap.org/data/2.5/weather";

    private final String apiKey;
    private final WeatherHttpTransport transport;

    public OpenWeatherMapClient(String apiKey) {
        this(apiKey, new JavaHttpTransport());
    }

    OpenWeatherMapClient(String apiKey, WeatherHttpTransport transport) {
        this.apiKey = apiKey;
        this.transport = transport;
    }

    @Override
    public WeatherRecord fetch(String city) throws WeatherApiException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new WeatherApiException("API key is not configured");
        }
        try {
            String url = ENDPOINT + "?q=" + URLEncoder.encode(city, StandardCharsets.UTF_8)
                    + "&appid=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8) + "&units=metric";
            return parse(city, transport.get(url));
        } catch (IOException e) {
            throw new WeatherApiException("Weather API request failed", e);
        }
    }

    static WeatherRecord parse(String city, String json) throws WeatherApiException {
        if (!isCompleteJsonObject(json)) {
            throw new WeatherApiException("Weather API returned malformed JSON");
        }
        try {
            return new WeatherRecord(LocalDateTime.now(), city,
                    requiredNumber(json, "\\\"temp\\\"\\s*:\\s*([-+]?[0-9]*\\.?[0-9]+)"),
                    requiredNumber(json, "\\\"humidity\\\"\\s*:\\s*([-+]?[0-9]*\\.?[0-9]+)"),
                    requiredNumber(json, "\\\"pressure\\\"\\s*:\\s*([-+]?[0-9]*\\.?[0-9]+)"));
        } catch (NumberFormatException e) {
            throw new WeatherApiException("Weather API returned an invalid numeric value", e);
        }
    }

    private static double requiredNumber(String json, String regex) throws WeatherApiException {
        Matcher matcher = Pattern.compile(regex).matcher(json);
        if (!matcher.find()) {
            throw new WeatherApiException("Weather API response is missing a required field");
        }
        return Double.parseDouble(matcher.group(1));
    }

    private static boolean isCompleteJsonObject(String json) {
        if (json == null || json.isBlank() || !json.strip().startsWith("{") || !json.strip().endsWith("}")) {
            return false;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (char character : json.toCharArray()) {
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    inString = false;
                }
            } else if (character == '"') {
                inString = true;
            } else if (character == '{') {
                depth++;
            } else if (character == '}' && --depth < 0) {
                return false;
            }
        }
        return !inString && depth == 0;
    }

    private static final class JavaHttpTransport implements WeatherHttpTransport {
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        @Override
        public String get(String url) throws IOException {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new IOException("HTTP " + response.statusCode());
                }
                return response.body();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Weather API request interrupted", e);
            }
        }
    }
}
