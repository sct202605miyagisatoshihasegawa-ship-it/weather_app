package weather_app;
/*
 * グラフィカルな画面（Swing）で都市の切り替えができ
 *裏で疑似的にデータをCSVに保存・読み込みしてグラフ化するコードのサンプルです。
 */
import java.time.LocalDateTime;

public class WeatherRecord {
    LocalDateTime dateTime;
    String city;
    double temperature;
    double humidity;
    double pressure;

    public WeatherRecord(LocalDateTime dateTime, String city, double temperature, double humidity, double pressure) {
        this.dateTime = dateTime;
        this.city = city;
        this.temperature = temperature;
        this.humidity = humidity;
        this.pressure = pressure;
    }

    public String toCsvRow() {
        return dateTime.toString() + "," + city + "," + temperature + "," + humidity + "," + pressure;
    }
}