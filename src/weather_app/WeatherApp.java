package weather_app;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
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
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.SpinnerNumberModel;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.Minute;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

public class WeatherApp extends JFrame {
	private final String[] CITIES = {
			"Sendai,JP", "Tokyo,JP", "Niigata,JP",
			"London,GB", "Mumbai,IN", "Sydney,AU",
			"Beijing,CN", "Madrid,ES", "New York,US"
	};

	private WeatherApiClient weatherApiClient;
	private SettingsService settingsService;
	private AppSettings settings;
	private final PressureAlertService pressureAlertService = new PressureAlertService();
	private WeatherDataService weatherDataService;
	private AlertNotifier alertNotifier;
	private JComboBox<String> graphPeriodSelector;
	private JTextField graphFromField;
	private JTextField graphToField;
	private GraphPeriod displayedGraphPeriod;

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
		try {
			settingsService = new SettingsService(WeatherAppPaths.settingsFile());
			settings = settingsService.load();
			weatherApiClient = new OpenWeatherMapClient(settings.apiKey());
		} catch (IOException e) {
			throw new IllegalStateException("Could not load application settings", e);
		}
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
		JButton settingsButton = new JButton("設定");
		settingsButton.addActionListener(e -> showSettingsDialog());
		headerPanel.add(settingsButton, BorderLayout.CENTER);
		headerPanel.add(alertLabel, BorderLayout.EAST);
		JPanel northPanel = new JPanel(new BorderLayout());
		northPanel.add(headerPanel, BorderLayout.NORTH);
		northPanel.add(createGraphPeriodPanel(), BorderLayout.SOUTH);
		add(northPanel, BorderLayout.NORTH);

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
		JButton csvImportButton = new JButton("CSV移行");
		notificationTestButton.addActionListener(e -> runNotificationTest());
		stopNotificationButton.addActionListener(e -> alertNotifier.stop());
		csvImportButton.addActionListener(e -> chooseAndImportCsv());
		testPanel.add(notificationTestButton);
		testPanel.add(stopNotificationButton);
		testPanel.add(csvImportButton);
		southPanel.add(testPanel, BorderLayout.SOUTH);

		add(southPanel, BorderLayout.SOUTH);

		loadDatabaseToGraphAndStartCollection();
	}

	private JPanel createGraphPeriodPanel() {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		graphPeriodSelector = new JComboBox<>(new String[] { "1日", "1週間", "1年", "任意期間" });
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
		LocalDateTime now = LocalDateTime.now();
		graphFromField = new JTextField(formatter.format(now.minusDays(1)), 16);
		graphToField = new JTextField(formatter.format(now), 16);
		JButton updateButton = new JButton("表示更新");
		graphPeriodSelector.addActionListener(e -> updateCustomPeriodFields());
		updateButton.addActionListener(e -> reloadSelectedGraph());
		panel.add(new JLabel("表示期間"));
		panel.add(graphPeriodSelector);
		panel.add(new JLabel("開始"));
		panel.add(graphFromField);
		panel.add(new JLabel("終了"));
		panel.add(graphToField);
		panel.add(updateButton);
		updateCustomPeriodFields();
		return panel;
	}

	private void updateCustomPeriodFields() {
		boolean custom = "任意期間".equals(graphPeriodSelector.getSelectedItem());
		graphFromField.setEnabled(custom);
		graphToField.setEnabled(custom);
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
		startDataCollectionTimer(true);
	}

	private void startDataCollectionTimer(boolean collectImmediately) {
		try {
			WeatherRepository repository = new WeatherRepository(WeatherAppPaths.databaseFile());
			weatherDataService = new WeatherDataService(List.of(CITIES), weatherApiClient, repository,
					this::processNewRecord, this::handleCollectionFailure, this::updateCollectionStatus);
			Duration interval = Duration.ofMinutes(settings.collectionIntervalMinutes());
			weatherDataService.start(interval, collectImmediately ? Duration.ZERO : interval);
		} catch (IOException | SQLException e) {
			throw new IllegalStateException("Could not start weather data collection", e);
		}
	}

	private void showSettingsDialog() {
		JDialog dialog = new JDialog(this, "設定", true);
		JPanel panel = new JPanel(new GridLayout(3, 2, 8, 8));
		JPasswordField apiKeyField = new JPasswordField(settings.apiKey(), 24);
		JSpinner intervalSpinner = new JSpinner(new SpinnerNumberModel(settings.collectionIntervalMinutes(), 1, 1440, 1));
		JButton saveButton = new JButton("保存");
		JButton cancelButton = new JButton("キャンセル");

		panel.add(new JLabel("OpenWeatherMap APIキー"));
		panel.add(apiKeyField);
		panel.add(new JLabel("取得間隔（分）"));
		panel.add(intervalSpinner);
		panel.add(cancelButton);
		panel.add(saveButton);
		cancelButton.addActionListener(e -> dialog.dispose());
		saveButton.addActionListener(e -> {
			try {
				applySettings(new AppSettings(new String(apiKeyField.getPassword()).trim(), (Integer) intervalSpinner.getValue()));
				dialog.dispose();
			} catch (IOException | SQLException ex) {
				JOptionPane.showMessageDialog(dialog, "設定を保存できません: " + ex.getMessage(), "設定エラー", JOptionPane.ERROR_MESSAGE);
			}
		});
		dialog.setContentPane(panel);
		dialog.pack();
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
	}

	private void applySettings(AppSettings updatedSettings) throws IOException, SQLException {
		settingsService.save(updatedSettings);
		if (weatherDataService != null) {
			weatherDataService.close();
		}
		settings = updatedSettings;
		weatherApiClient = new OpenWeatherMapClient(settings.apiKey());
		startDataCollectionTimer(false);
		collectionStatusLabel.setText("設定を保存しました。次回取得を待機中");
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
			if (displayedGraphPeriod == null || !displayedGraphPeriod.contains(record.dateTime())) return;
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

	private void loadDatabaseToGraphAndStartCollection() {
		loadGraph(GraphPeriod.lastDay(LocalDateTime.now()), true);
	}

	private void reloadSelectedGraph() {
		try {
			LocalDateTime now = LocalDateTime.now();
			GraphPeriod period = switch ((String) graphPeriodSelector.getSelectedItem()) {
			case "1日" -> GraphPeriod.lastDay(now);
			case "1週間" -> GraphPeriod.lastWeek(now);
			case "1年" -> GraphPeriod.lastYear(now);
			case "任意期間" -> GraphPeriod.custom(parseGraphDate(graphFromField.getText()), parseGraphDate(graphToField.getText()));
			default -> throw new IllegalStateException("未知の表示期間です");
			};
			loadGraph(period, false);
		} catch (IllegalArgumentException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "表示期間エラー", JOptionPane.ERROR_MESSAGE);
		}
	}

	private LocalDateTime parseGraphDate(String text) {
		try {
			return LocalDateTime.parse(text.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
		} catch (Exception e) {
			throw new IllegalArgumentException("日時は yyyy-MM-dd HH:mm 形式で入力してください");
		}
	}

	private void loadGraph(GraphPeriod period, boolean startCollectionAfterLoad) {
		new SwingWorker<List<WeatherRecord>, Void>() {
			@Override
			protected List<WeatherRecord> doInBackground() throws Exception {
				try (WeatherRepository repository = new WeatherRepository(WeatherAppPaths.databaseFile())) {
					return new WeatherGraphDataService().load(repository, List.of(CITIES), period);
				}
			}

			@Override
			protected void done() {
				try {
					replaceGraphRecords(get());
					displayedGraphPeriod = period;
					if (startCollectionAfterLoad) startDataCollectionTimer();
				} catch (Exception e) {
					JOptionPane.showMessageDialog(WeatherApp.this, "SQLiteデータを読み込めません: " + e.getMessage(),
							"データ読み込みエラー", JOptionPane.ERROR_MESSAGE);
				}
			}
		}.execute();
	}

	private void replaceGraphRecords(List<WeatherRecord> records) {
		updateGraphsInBatch(() -> {
			sendaiHistory.clear();
			for (TimeSeries series : tempSeriesMap.values()) series.clear();
			for (TimeSeries series : humidSeriesMap.values()) series.clear();
			for (TimeSeries series : pressSeriesMap.values()) series.clear();
			for (WeatherRecord record : records) addRecordToGraphs(record);
		});
	}

	private void chooseAndImportCsv() {
		JFileChooser chooser = new JFileChooser();
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			importCsv(chooser.getSelectedFile().toPath());
		}
	}

	private void importCsv(Path csvFile) {
		new SwingWorker<CsvImportResult, Void>() {
			@Override
			protected CsvImportResult doInBackground() throws Exception {
				List<WeatherRecord> records = new WeatherCsvImporter().readAll(csvFile);
				try (WeatherRepository repository = new WeatherRepository(WeatherAppPaths.databaseFile())) {
					String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS").format(LocalDateTime.now());
					repository.backupTo(WeatherAppPaths.backupsDirectory().resolve("weather-before-csv-import-" + timestamp + ".db"));
					return repository.importIfAbsent(records);
				}
			}

			@Override
			protected void done() {
				try {
					CsvImportResult result = get();
					if (displayedGraphPeriod != null) loadGraph(displayedGraphPeriod, false);
					JOptionPane.showMessageDialog(WeatherApp.this,
							"CSV移行が完了しました\n追加: " + result.importedCount() + "件\n重複スキップ: " + result.skippedDuplicateCount() + "件\n不正行: 0件",
							"CSV移行", JOptionPane.INFORMATION_MESSAGE);
				} catch (Exception e) {
					Throwable cause = e.getCause() == null ? e : e.getCause();
					if (cause instanceof WeatherCsvFormatException formatError) {
						JOptionPane.showMessageDialog(WeatherApp.this,
								"CSVの不正行: " + formatError.invalidRowCount() + "件\nSQLiteは変更していません。\n" + formatError.getMessage(),
								"CSV移行エラー", JOptionPane.ERROR_MESSAGE);
					} else {
						JOptionPane.showMessageDialog(WeatherApp.this, "CSV移行に失敗しました: " + cause.getMessage(),
								"CSV移行エラー", JOptionPane.ERROR_MESSAGE);
					}
				}
			}
		}.execute();
	}

	private void addRecordToGraphs(WeatherRecord record) {
		if (record.city().equals("Sendai,JP")) sendaiHistory.add(record);
		if (!tempSeriesMap.containsKey(record.city())) return;
		Date date = Date.from(record.dateTime().atZone(ZoneId.systemDefault()).toInstant());
		Minute minute = new Minute(date);
		tempSeriesMap.get(record.city()).addOrUpdate(minute, record.temperature());
		humidSeriesMap.get(record.city()).addOrUpdate(minute, record.humidity());
		pressSeriesMap.get(record.city()).addOrUpdate(minute, record.pressure());
	}

	/** Prevents a repaint for every historical observation; one repaint follows the whole batch. */
	private void updateGraphsInBatch(Runnable update) {
		tempDataset.setNotify(false);
		humidDataset.setNotify(false);
		pressDataset.setNotify(false);
		try {
			update.run();
		} finally {
			tempDataset.setNotify(true);
			humidDataset.setNotify(true);
			pressDataset.setNotify(true);
		}
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> new WeatherApp().setVisible(true));
	}
}
