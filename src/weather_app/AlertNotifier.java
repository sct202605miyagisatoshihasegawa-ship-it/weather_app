package weather_app;

/** Presents and plays a notification for a pressure alert. */
public interface AlertNotifier extends AutoCloseable {
    void notifyAlert(PressureAlert alert);

    void stop();

    @Override
    void close();
}
