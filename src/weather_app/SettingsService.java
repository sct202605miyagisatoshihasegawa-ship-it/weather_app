package weather_app;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Loads and saves the local settings.properties file. */
public final class SettingsService {
    private static final String API_KEY_PROPERTY = "openweather.api.key";
    private static final String INTERVAL_PROPERTY = "collection.interval.minutes";

    private final Path settingsFile;

    public SettingsService(Path settingsFile) {
        this.settingsFile = settingsFile;
    }

    public AppSettings load() throws IOException {
        if (!Files.exists(settingsFile)) {
            return AppSettings.defaults();
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(settingsFile)) {
            properties.load(input);
        }
        String apiKey = properties.getProperty(API_KEY_PROPERTY, "").trim();
        String intervalText = properties.getProperty(INTERVAL_PROPERTY,
                String.valueOf(AppSettings.DEFAULT_COLLECTION_INTERVAL_MINUTES));
        try {
            return new AppSettings(apiKey, Integer.parseInt(intervalText));
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid collection interval in " + settingsFile, e);
        }
    }

    public void save(AppSettings settings) throws IOException {
        Files.createDirectories(settingsFile.getParent());
        Properties properties = new Properties();
        properties.setProperty(API_KEY_PROPERTY, settings.apiKey());
        properties.setProperty(INTERVAL_PROPERTY, String.valueOf(settings.collectionIntervalMinutes()));
        try (OutputStream output = Files.newOutputStream(settingsFile)) {
            properties.store(output, "Weather App settings");
        }
    }
}
