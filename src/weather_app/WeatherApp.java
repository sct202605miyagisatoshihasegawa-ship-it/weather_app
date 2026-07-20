package weather_app;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

	private final WeatherApiClient weatherApiClient;
	private final PressureAlertService pressureAlertService = new PressureAlertService();
	private WeatherDataService weatherDataService;
	private AlertNotifier alertNotifier;

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
	private JLabel collectionStatusLabel;

	public WeatherApp() {
		this(new OpenWeatherMapClient(API_KEY));
	}

	WeatherApp(WeatherApiClient weatherApiClient) {
		this.weatherApiClient = weatherApiClient;
		setTitle("お天気データロガー（世界主要都市）ver2.0");
		setSize(1000, 650);
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		setLocationRelativeTo(null);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent event) {
				shutdownApplication();
			}
		});

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
		collectionStatusLabel = new JLabel("待機中", SwingConstants.LEFT);
		collectionStatusLabel.setFont(new Font("MS Gothic", Font.PLAIN, 12));
		headerPanel.add(collectionStatusLabel, BorderLayout.WEST);
		alertLabel = new JLabel(" ", SwingConstants.RIGHT);
		alertLabel.setFont(new Font("MS Gothic", Font.BOLD, 14));
		alertLabel.setForeground(Color.RED);
		alertNotifier = new ScheduledAlertNotifier(
				text -> SwingUtilities.invokeLater(() -> alertLabel.setText(text + "   ")),
				() -> Toolkit.getDefaultToolkit().beep());
		headerPanel.add(alertLabel, BorderLayout.EAST);
		add(headerPanel, BorderLayout.NORTH);

		JTabbedPane tabbedPane = new JTabbedPane();
		tabbedPane.addTab("気温", new ChartPanel(tempChart));
		tabbedPane.addTab("湿度", new ChartPanel(humidChart));
		tabbedPane.addTab("気圧", new ChartPanel(pressChart));
		add(tabbedPane, BorderLayout.CENTER);

		// 4. 下部の都市切り替えチェックボックスと通知操作
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

		// 通知テストは観測データ・SQLite・CSV・グラフを変更しない
		JPanel testPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton notificationTestButton = new JButton("通知テスト");
		JButton stopNotificationButton = new JButton("通知を停止");
		notificationTestButton.addActionListener(e -> runNotificationTest());
		stopNotificationButton.addActionListener(e -> alertNotifier.stop());
		testPanel.add(notificationTestButton);
		testPanel.add(stopNotificationButton);
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
		try {
			WeatherRepository repository = new WeatherRepository(WeatherAppPaths.databaseFile());
			weatherDataService = new WeatherDataService(List.of(CITIES), weatherApiClient, repository,
					this::processNewRecord, this::handleCollectionFailure, this::updateCollectionStatus);
			weatherDataService.start(Duration.ofMinutes(15));
		} catch (IOException | SQLException e) {
			throw new IllegalStateException("Could not start weather data collection", e);
		}
	}

	private void handleCollectionFailure(String city, Exception error) {
		System.err.println("Weather collection failed (" + city + "): " + error.getMessage());
	}

	private void updateCollectionStatus(WeatherCollectionStatus status) {
		SwingUtilities.invokeLater(() -> {
			String timestamp = status.timestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
			switch (status.state()) {
			case FETCHING -> collectionStatusLabel.setText("取得中...");
			case SUCCESS -> collectionStatusLabel.setText("最終成功: " + timestamp);
			case FAILURE -> collectionStatusLabel.setText(status.detail() + "（連続" + status.consecutiveFailures() + "回）");
			}
		});
	}

	// 🔍 新しいデータを処理・反映する共通メソッド（API経由・ダミー共通）
	private void processNewRecord(WeatherRecord record) {
		if (record.city().equals("Sendai,JP")) {
			checkPressureFluctuation(record);
			sendaiHistory.add(record);
		}

		Date date = Date.from(record.dateTime().atZone(ZoneId.systemDefault()).toInstant());
		Minute minute = new Minute(date);

		SwingUtilities.invokeLater(() -> {
			tempSeriesMap.get(record.city()).addOrUpdate(minute, record.temperature());
			humidSeriesMap.get(record.city()).addOrUpdate(minute, record.humidity());
			pressSeriesMap.get(record.city()).addOrUpdate(minute, record.pressure());
		});
	}

	private void runNotificationTest() {
		LocalDateTime now = LocalDateTime.now();
		WeatherRecord comparison = new WeatherRecord(now.minusHours(1), "Sendai,JP", 20.0, 60.0, 1000.0);
		WeatherRecord current = new WeatherRecord(now, "Sendai,JP", 20.0, 60.0, 1003.0);
		alertNotifier.notifyAlert(new PressureAlert(current, comparison, 3.0));
	}

	private void shutdownApplication() {
		collectionStatusLabel.setText("終了処理中...");
		alertNotifier.close();
		if (weatherDataService != null) {
			try {
				weatherDataService.close();
			} catch (SQLException e) {
				System.err.println("Could not close weather data service: " + e.getMessage());
			}
		}
		dispose();
	}

	private void checkPressureFluctuation(WeatherRecord current) {
		pressureAlertService.evaluate(current, sendaiHistory).ifPresent(alertNotifier::notifyAlert);
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
