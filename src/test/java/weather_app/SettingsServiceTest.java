package weather_app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void returnsDefaultsWhenTheSettingsFileDoesNotExist() throws Exception {
        AppSettings settings = new SettingsService(temporaryDirectory.resolve("settings.properties")).load();

        assertEquals("", settings.apiKey());
        assertEquals(15, settings.collectionIntervalMinutes());
    }

    @Test
    void savesAndReloadsApiKeyAndCollectionInterval() throws Exception {
        SettingsService service = new SettingsService(temporaryDirectory.resolve("nested/settings.properties"));
        AppSettings saved = new AppSettings("dummy-api-key", 30);

        service.save(saved);

        assertEquals(saved, service.load());
    }
}
