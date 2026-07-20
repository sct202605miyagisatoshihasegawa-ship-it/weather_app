package weather_app;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.Minute;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

public class WeatherApp extends JFrame {
	// ⚠️ ご自身のAPIキーに書き換えてください
	private static final String API_KEY = "YOUR_API_KEY_HERE";

	private final String[] CITIES = {
			"Sendai,JP", "Tokyo,JP", "Niigata,JP",
			"London,GB", "Mumbai,IN", "Sydney,AU",
			"Beijing,CN", "Madrid,ES", "New York,US"
	};

	private static final String CSV_FILE = System.getProperty("user.home") + File.separator + "Desktop" + File.separator
			+ "weather_data.csv";

	private TimeSeriesCollection tempDataset = new TimeSeriesCollection();
	private TimeSeriesCollection humidDataset = new TimeSeriesCollection();
	private TimeSeriesCollection pressDataset = new TimeSeriesCollection();

	private Map<String, TimeSeries> tempSeriesMap = new HashMap<>();
	private Map<String, TimeSeries> humidSeriesMap = new HashMap<>();
	private Map<String, TimeSeries> pressSeriesMap = new HashMap<>();

	// 🔍 仙台の過去データを判定用に記録しておくリスト
	private List<WeatherRecord> sendaiHistory = new ArrayList<>();

	// 🔍 右上の警告表示用ラベル
	private JLabel alertLabel;

	// 🔍 最後に保存された仙台の気圧（テストボタン用）
	private double lastSendaiPressure = 1013.0;

	public WeatherApp() {
		setTitle("お天気データロガー（世界主要都市）ver2.0");
		setSize(1000, 650);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLocationRelativeTo(null);

		initDatasets();

		JFreeChart tempChart = ChartFactory.createTimeSeriesChart("気温の変化", "時間", "気温 (℃)", tempDataset, true, true,
				false);
		JFreeChart humidChart = ChartFactory.createTimeSeriesChart("湿度の変化", "時間", "湿度 (%)", humidDataset, true, true,
				false);
		JFreeChart pressChart = ChartFactory.createTimeSeriesChart("気圧の変化", "時間", "気圧 (hPa)", pressDataset, true, true,
				false);

		java.awt.Font titleFont = new java.awt.Font("MS Gothic", java.awt.Font.BOLD, 18);
		java.awt.Font commonFont = new java.awt.Font("MS Gothic", java.awt.Font.PLAIN, 12);

		tempChart.getTitle().setFont(titleFont);
		tempChart.getXYPlot().getDomainAxis().setLabelFont(commonFont);
		tempChart.getXYPlot().getRangeAxis().setLabelFont(commonFont);
		if (tempChart.getLegend() != null)
			tempChart.getLegend().setItemFont(commonFont);

		humidChart.getTitle().setFont(titleFont);
		humidChart.getXYPlot().getDomainAxis().setLabelFont(commonFont);
		humidChart.getXYPlot().getRangeAxis().setLabelFont(commonFont);
		if (humidChart.getLegend() != null)
			humidChart.getLegend().setItemFont(commonFont);

		pressChart.getTitle().setFont(titleFont);
		pressChart.getXYPlot().getDomainAxis().setLabelFont(commonFont);
		pressChart.getXYPlot().getRangeAxis().setLabelFont(commonFont);
		if (pressChart.getLegend() != null)
			pressChart.getLegend().setItemFont(commonFont);

		// 🔍 右上の警告ラベルの作成（ヘッダーパネルとして最上部に追加）
		JPanel headerPanel = new JPanel(new BorderLayout());
		alertLabel = new JLabel(" ", SwingConstants.RIGHT);
		alertLabel.setFont(new Font("MS Gothic", Font.BOLD, 14));
		alertLabel.setForeground(Color.RED);
		headerPanel.add(alertLabel, BorderLayout.EAST);
		add(headerPanel, BorderLayout.NORTH);

		JTabbedPane tabbedPane = new JTabbedPane();
		tabbedPane.addTab("気温", new ChartPanel(tempChart));
		tabbedPane.addTab("湿度", new ChartPanel(humidChart));
		tabbedPane.addTab("気圧", new ChartPanel(pressChart));
		add(tabbedPane, BorderLayout.CENTER);

		// 4. 下部の都市切り替えチェックボックス ＆ 🛠️ テスト用デバッグボタン
		JPanel southPanel = new JPanel(new BorderLayout());

		JPanel controlPanel = new JPanel();
		for (String city : CITIES) {
			String displayName = city.split(",")[0];
			JCheckBox checkBox = new JCheckBox(displayName, true);
			checkBox.addActionListener(e -> {
				boolean selected = checkBox.isSelected();
				toggleCityDisplay(city, selected);
			});
			controlPanel.add(checkBox);
		}
		southPanel.add(controlPanel, BorderLayout.CENTER);

		// 🛠️ テストボタン用パネル（右下に配置）
		JPanel testPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton btnUp = new JButton("🧪 テスト: 気圧上昇(+3.5hPa)");
		JButton btnDown = new JButton("🧪 テスト: 気圧降下(-3.5hPa)");

		btnUp.addActionListener(e -> injectDummyData(3.5));
		btnDown.addActionListener(e -> injectDummyData(-3.5));

		testPanel.add(btnUp);
		testPanel.add(btnDown);
		southPanel.add(testPanel, BorderLayout.SOUTH);

		add(southPanel, BorderLayout.SOUTH);

		loadCsvToGraph();
		startDataCollectionTimer();
	}

	private void initDatasets() {
		for (String city : CITIES) {
			TimeSeries tSeries = new TimeSeries(city);
			TimeSeries hSeries = new TimeSeries(city);
			TimeSeries pSeries = new TimeSeries(city);

			tempSeriesMap.put(city, tSeries);
			humidSeriesMap.put(city, hSeries);
			pressSeriesMap.put(city, pSeries);

			tempDataset.addSeries(tSeries);
			humidDataset.addSeries(hSeries);
			pressDataset.addSeries(pSeries);
		}
	}

	private void toggleCityDisplay(String city, boolean show) {
		if (show) {
			if (!tempDataset.getSeries().contains(tempSeriesMap.get(city)))
				tempDataset.addSeries(tempSeriesMap.get(city));
			if (!humidDataset.getSeries().contains(humidSeriesMap.get(city)))
				humidDataset.addSeries(humidSeriesMap.get(city));
			if (!pressDataset.getSeries().contains(pressSeriesMap.get(city)))
				pressDataset.addSeries(pressSeriesMap.get(city));
		} else {
			tempDataset.removeSeries(tempSeriesMap.get(city));
			humidDataset.removeSeries(humidSeriesMap.get(city));
			pressDataset.removeSeries(pressSeriesMap.get(city));
		}
	}

	private void startDataCollectionTimer() {
		ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
		scheduler.scheduleAtFixedRate(() -> {
			System.out.println("[" + LocalDateTime.now() + "] OpenWeatherMapからデータ取得中...");
			for (String city : CITIES) {
				WeatherRecord record = fetchWeatherDataFromApi(city);
				if (record != null) {
					processNewRecord(record);
				}
			}
		}, 0, /*10, TimeUnit.SECONDS); */15, TimeUnit.MINUTES);
	}

	// 🔍 新しいデータを処理・反映する共通メソッド（API経由・ダミー共通）
	private void processNewRecord(WeatherRecord record) {
		saveToCsv(record);

		if (record.city().equals("Sendai,JP")) {
			lastSendaiPressure = record.pressure();
			sendaiHistory.add(record);
			// 🔍 過去1時間（15分間隔なら4個前、10秒間隔なら6個前）のデータと比較して音を鳴らす判定
			checkPressureFluctuation(record);
		}

		Date date = Date.from(record.dateTime().atZone(ZoneId.systemDefault()).toInstant());
		Minute minute = new Minute(date);

		SwingUtilities.invokeLater(() -> {
			tempSeriesMap.get(record.city()).addOrUpdate(minute, record.temperature());
			humidSeriesMap.get(record.city()).addOrUpdate(minute, record.humidity());
			pressSeriesMap.get(record.city()).addOrUpdate(minute, record.pressure());
		});
	}

	// 🔍 🛠️ デバッグ用：ボタンを押した時に強制的に急変動データを注入する
	private void injectDummyData(double diff) {
		System.out.println("[テストモード] 仙台のダミー気圧データを注入します (差分: " + diff + " hPa)");

		// 判定ロジックが「過去1時間前」を見るため、まずはダミーの「1時間前の過去ログ」を歴史に仕込む
		LocalDateTime now = LocalDateTime.now();
		WeatherRecord oldRecord = new WeatherRecord(now.minusHours(1), "Sendai,JP", 20.0, 60.0, lastSendaiPressure);
		sendaiHistory.add(oldRecord);

		// そして、今現在の「激変したデータ」を突っ込む
		double newPressure = lastSendaiPressure + diff;
		WeatherRecord currentDummy = new WeatherRecord(now, "Sendai,JP", 20.0, 60.0, newPressure);

		processNewRecord(currentDummy);
	}

	// 🔍 判定ロジック：案A（過去1時間前のデータと比較して3hPa以上の変動でアラート）
	private void checkPressureFluctuation(WeatherRecord current) {
		if (sendaiHistory.size() < 2)
			return;

		WeatherRecord targetOldRecord = null;
		LocalDateTime oneHourAgo = current.dateTime().minusHours(1);

		// 現在が10秒間隔テスト中の場合、1時間前が存在しないので「直近の最も古いデータ」を擬似的に1時間前とみなしてテストできるようにします
		if (sendaiHistory.get(0).dateTime().isAfter(oneHourAgo)) {
			targetOldRecord = sendaiHistory.get(0); // テスト用：手持ちで一番古いもの
		} else {
			// 本番用：1時間前に一番近いデータを歴史から探す
			for (WeatherRecord history : sendaiHistory) {
				if (!history.dateTime().isAfter(oneHourAgo)) {
					targetOldRecord = history;
				}
			}
		}
		if (targetOldRecord != null && targetOldRecord != current) {
			double diff = current.pressure() - targetOldRecord.pressure();

			if (Math.abs(diff) >= 3.0) {
				if (diff > 0) {
					// 気圧上昇：1回鳴らす
					System.out.println("⚠️ 仙台の気圧が急上昇！(" + String.format("%.1f", diff) + " hPa) 音を1回鳴らします。");
					updateAlertUI("⚠️ 仙台の気圧急上昇！(" + String.format("%.1f", diff) + " hPa)", 1);
				} else {
					// 気圧降下：3回鳴らす
					System.out.println("⚠️ 仙台の気圧が急降下！(" + String.format("%.1f", diff) + " hPa) 音を3回鳴らします。");
					updateAlertUI("⚠️ 仙台の気圧急降下！(" + String.format("%.1f", diff) + " hPa)", 3);
				}
			}
		}
	}

	// 🔍 UIの警告文字を書き換え、音を別スレッドで鳴らす（画面フリーズ対策）
	private void updateAlertUI(String text, int beepCount) {
		SwingUtilities.invokeLater(() -> alertLabel.setText(text + "   "));
		// 音を鳴らす（連続ビープで画面が固まらないよう別スレッドで実行）
		new Thread(() -> {
			for (int i = 0; i < beepCount; i++) {
				Toolkit.getDefaultToolkit().beep();
				try {
					Thread.sleep(1200);
					// ポーン、ポーンの間隔（1.2秒）
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}).start();
}
	private WeatherRecord fetchWeatherDataFromApi(String city) {
    		try {
    			 // 1. 都市名を安全にエンコード（スペースやカンマ対策）
                String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8.name());
                
                // 2. ⭕ これがデータを引き抜くための本物のAPI専用URLです（途中で切れないよう1行で記述しています）
                String urlStr = "https://api.openweathermap.org/data/2.5/weather?q=" + encodedCity + "&appid=" + API_KEY + "&units=metric";
                
                java.net.URI uri = java.net.URI.create(urlStr);
                java.net.URL url = uri.toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                
                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    br.close();
                    
                    double temp = Double.parseDouble(jsonExtract(response.toString(), "\"temp\":([\\d.-]+)"));
                    double humidity = Double.parseDouble(jsonExtract(response.toString(), "\"humidity\":([\\d.]+)"));
                    double pressure = Double.parseDouble(jsonExtract(response.toString(), "\"pressure\":([\\d.]+)"));
                    
                    return new WeatherRecord(LocalDateTime.now(), city, temp, humidity, pressure);
                } else {
                    System.err.println("APIエラー (" + city + "): HTTP " + conn.getResponseCode());
                }
            } catch (Exception e) {
                System.err.println("データ取得失敗 (" + city + "): " + e.getMessage());
            }
            return null;
    			}

	private String jsonExtract(String json, String regex) {
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(json);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return "0";
	}

	private synchronized void saveToCsv(WeatherRecord record) {
		try (FileWriter fw = new FileWriter(CSV_FILE, true);
				PrintWriter out = new PrintWriter(new BufferedWriter(fw))) {
			out.println(record.toCsvRow());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void loadCsvToGraph() {
		File file = new File(CSV_FILE);
		if (!file.exists())
			return;

		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] data = line.split(",");
				if (data.length < 5)
					continue;

				LocalDateTime dt = LocalDateTime.parse(data[0]);
				String city = data[1];
				double temp = Double.parseDouble(data[2]);
				double humid = Double.parseDouble(data[3]);
				double press = Double.parseDouble(data[4]);
				WeatherRecord record = new WeatherRecord(dt, city, temp, humid, press);
				if (city.equals("Sendai,JP")) {
					sendaiHistory.add(record);
					lastSendaiPressure = press;
				}
				if (tempSeriesMap.containsKey(city)) {
					Date date = Date.from(dt.atZone(ZoneId.systemDefault()).toInstant());
					Minute minute = new Minute(date);
					tempSeriesMap.get(city).addOrUpdate(minute, temp);
					humidSeriesMap.get(city).addOrUpdate(minute, humid);
					pressSeriesMap.get(city).addOrUpdate(minute, press);
				}
			}
		} catch (Exception e) {
			System.err.println("CSV読み込みスキップ: " + e.getMessage());
		}
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> new WeatherApp().setVisible(true));
	}
}

