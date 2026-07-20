package weather_app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class PressureAlertServiceTest {
    private final PressureAlertService service = new PressureAlertService();
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 20, 12, 0);

    @Test
    void alertsForAThreeHpaRiseAgainstTheObservationSixtyMinutesAgo() {
        WeatherRecord current = record(now, "Sendai,JP", 1003.0);

        Optional<PressureAlert> alert = service.evaluate(current, List.of(record(now.minusMinutes(60), "Sendai,JP", 1000.0)));

        assertTrue(alert.isPresent());
        assertTrue(alert.get().isRising());
        assertEquals(3.0, alert.get().pressureDifference());
    }

    @Test
    void alertsForAFallAndKeepsTheSignedPressureDifference() {
        WeatherRecord current = record(now, "Sendai,JP", 997.0);

        Optional<PressureAlert> alert = service.evaluate(current, List.of(record(now.minusMinutes(60), "Sendai,JP", 1000.0)));

        assertTrue(alert.isPresent());
        assertFalse(alert.get().isRising());
        assertEquals(-3.0, alert.get().pressureDifference());
    }

    @Test
    void usesTheCandidateClosestToSixtyMinutesAgo() {
        WeatherRecord current = record(now, "Sendai,JP", 1007.0);
        WeatherRecord fiftyMinutesAgo = record(now.minusMinutes(50), "Sendai,JP", 1000.0);
        WeatherRecord sixtyFiveMinutesAgo = record(now.minusMinutes(65), "Sendai,JP", 1003.0);

        Optional<PressureAlert> alert = service.evaluate(current, List.of(fiftyMinutesAgo, sixtyFiveMinutesAgo));

        assertTrue(alert.isPresent());
        assertEquals(sixtyFiveMinutesAgo, alert.get().comparisonRecord());
        assertEquals(4.0, alert.get().pressureDifference());
    }

    @Test
    void ignoresObservationsOutsideTheFortyFiveToSeventyFiveMinuteWindow() {
        WeatherRecord current = record(now, "Sendai,JP", 1005.0);

        Optional<PressureAlert> alert = service.evaluate(current, List.of(
                record(now.minusMinutes(44), "Sendai,JP", 1000.0),
                record(now.minusMinutes(76), "Sendai,JP", 1000.0)));

        assertTrue(alert.isEmpty());
    }

    @Test
    void doesNotAlertForOtherCitiesOrChangesBelowTheThreshold() {
        assertTrue(service.evaluate(record(now, "Tokyo,JP", 1005.0),
                List.of(record(now.minusMinutes(60), "Tokyo,JP", 1000.0))).isEmpty());
        assertTrue(service.evaluate(record(now, "Sendai,JP", 1002.9),
                List.of(record(now.minusMinutes(60), "Sendai,JP", 1000.0))).isEmpty());
    }

    private static WeatherRecord record(LocalDateTime timestamp, String city, double pressure) {
        return new WeatherRecord(timestamp, city, 20.0, 60.0, pressure);
    }
}
