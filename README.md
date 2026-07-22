# お天気データロガー ver2.0

Windows 11 向けの Java Swing 製お天気アプリです。OpenWeatherMap から世界の主要都市の気温・湿度・気圧を定期取得し、SQLite に保存してグラフ表示します。

現在は個人開発のローカル動作検証版です。第三者へのMSI配布は行いません。医療上の判断や重要な用途には使用しないでください。

## 主な機能

- 9都市の気温・湿度・気圧を OpenWeatherMap から定期取得
- 「気温」「湿度」「気圧」「取得ログ」の4タブによる表示
- 期間を指定したグラフ表示（1日・1週間・1年・任意期間）
- SQLite による取得データの保存と、起動時のグラフ復元
- 既存CSVの手動インポート、重複スキップ、インポート前バックアップ
- 仙台の気圧が約1時間前から 3.0 hPa 以上変動した場合の通知
- APIキーと取得間隔をアプリ内の「設定」画面で管理

## 動作環境

- Windows 11
- Java 21
- Maven

## ローカル実行と初回設定

1. Mavenで依存ライブラリを解決してから、Eclipseで `WeatherApp.java` を実行します。
2. または、次のコマンドで実行用JARを作成して起動します。

```powershell
mvn --batch-mode clean package
java -jar target/weather-app.jar
```

3. アプリを起動し、左上の「設定」を開きます。
4. [OpenWeatherMap](https://openweathermap.org/) で取得した Current Weather API キーと、取得間隔を保存します。

APIキーは `%LOCALAPPDATA%\WeatherApp\settings.properties` に保存されます。ソースコードやGitへAPIキーを書き込まないでください。

## データの保存先

| 内容 | 保存先 |
| --- | --- |
| 設定、SQLite DB、バックアップ | `%LOCALAPPDATA%\WeatherApp` |

通常の取得データはSQLiteデータベースへ保存されます。CSVは旧データを移行するための手動インポート専用であり、CSVへの自動保存や起動時のCSV自動読込は行いません。

## 開発者向けビルド

```powershell
mvn --batch-mode clean verify
```

Maven は Java 21 でコンパイル、テスト、実行用JARの作成を行います。GitHub Actions でも同じ検証を実行します。

## ライセンス

使用している第三者ライブラリとライセンスは [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) を参照してください。
