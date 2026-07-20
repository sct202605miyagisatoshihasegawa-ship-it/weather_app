package weather_app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves the per-user application-data location without putting it in source control. */
final class WeatherAppPaths {
    private WeatherAppPaths() {
    }

    static Path databaseFile() throws IOException {
        return applicationDirectory().resolve("weather.db");
    }

    static Path settingsFile() throws IOException {
        return applicationDirectory().resolve("settings.properties");
    }

    static Path backupsDirectory() throws IOException {
        Path backupsDirectory = applicationDirectory().resolve("backups");
        Files.createDirectories(backupsDirectory);
        return backupsDirectory;
    }

    private static Path applicationDirectory() throws IOException {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path applicationDirectory = localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".weather-app")
                : Path.of(localAppData, "WeatherApp");
        Files.createDirectories(applicationDirectory);
        return applicationDirectory;
    }
}
