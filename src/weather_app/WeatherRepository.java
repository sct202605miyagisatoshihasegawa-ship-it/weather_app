package weather_app;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** SQLite persistence boundary. Its database location is injected for production and tests. */
public final class WeatherRepository implements AutoCloseable {
    private final Connection connection;

    public WeatherRepository(Path databaseFile) throws SQLException {
        this(openConnection(databaseFile));
    }

    private static Connection openConnection(Path databaseFile) throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver is unavailable", e);
        }
        return DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
    }

    WeatherRepository(Connection connection) throws SQLException {
        this.connection = connection;
        createSchema();
    }

    private void createSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS weather_observations (
                      observed_at TEXT NOT NULL,
                      city TEXT NOT NULL,
                      temperature REAL NOT NULL,
                      humidity REAL NOT NULL,
                      pressure REAL NOT NULL,
                      PRIMARY KEY (observed_at, city)
                    )
                    """);
        }
    }

    public void save(WeatherRecord record) throws SQLException {
        String sql = "INSERT OR REPLACE INTO weather_observations "
                + "(observed_at, city, temperature, humidity, pressure) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.dateTime().toString());
            statement.setString(2, record.city());
            statement.setDouble(3, record.temperature());
            statement.setDouble(4, record.humidity());
            statement.setDouble(5, record.pressure());
            statement.executeUpdate();
        }
    }

    public List<WeatherRecord> find(LocalDateTime fromInclusive, LocalDateTime toExclusive, String city) throws SQLException {
        String sql = "SELECT observed_at, city, temperature, humidity, pressure FROM weather_observations "
                + "WHERE city = ? AND observed_at >= ? AND observed_at < ? ORDER BY observed_at";
        List<WeatherRecord> records = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, city);
            statement.setString(2, fromInclusive.toString());
            statement.setString(3, toExclusive.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    records.add(new WeatherRecord(LocalDateTime.parse(result.getString("observed_at")), result.getString("city"),
                            result.getDouble("temperature"), result.getDouble("humidity"), result.getDouble("pressure")));
                }
            }
        }
        return records;
    }

    public List<WeatherRecord> findAll() throws SQLException {
        String sql = "SELECT observed_at, city, temperature, humidity, pressure FROM weather_observations "
                + "ORDER BY observed_at, city";
        List<WeatherRecord> records = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                records.add(new WeatherRecord(LocalDateTime.parse(result.getString("observed_at")), result.getString("city"),
                        result.getDouble("temperature"), result.getDouble("humidity"), result.getDouble("pressure")));
            }
        }
        return records;
    }

    /** Inserts a validated CSV batch without replacing observations that already exist. */
    public CsvImportResult importIfAbsent(List<WeatherRecord> records) throws SQLException {
        String sql = "INSERT OR IGNORE INTO weather_observations "
                + "(observed_at, city, temperature, humidity, pressure) VALUES (?, ?, ?, ?, ?)";
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        int imported = 0;
        List<WeatherRecord> importedRecords = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (WeatherRecord record : records) {
                statement.setString(1, record.dateTime().toString());
                statement.setString(2, record.city());
                statement.setDouble(3, record.temperature());
                statement.setDouble(4, record.humidity());
                statement.setDouble(5, record.pressure());
                if (statement.executeUpdate() == 1) {
                    imported++;
                    importedRecords.add(record);
                }
            }
            connection.commit();
            return new CsvImportResult(imported, records.size() - imported, importedRecords);
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    /** Creates a SQLite-consistent backup, including committed WAL content. */
    public void backupTo(Path backupFile) throws SQLException {
        String escapedPath = backupFile.toAbsolutePath().toString().replace("'", "''");
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("VACUUM INTO '" + escapedPath + "'");
        }
    }

    public List<DailyWeatherSummary> findDailySummaries(LocalDate fromInclusive, LocalDate toInclusive, String city) throws SQLException {
        String sql = "SELECT substr(observed_at, 1, 10) AS day, city, AVG(temperature) AS avg_temperature, "
                + "AVG(humidity) AS avg_humidity, AVG(pressure) AS avg_pressure, COUNT(*) AS count "
                + "FROM weather_observations WHERE city = ? AND observed_at >= ? AND observed_at < ? "
                + "GROUP BY substr(observed_at, 1, 10), city ORDER BY day";
        List<DailyWeatherSummary> summaries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, city);
            statement.setString(2, fromInclusive.atStartOfDay().toString());
            statement.setString(3, toInclusive.plusDays(1).atStartOfDay().toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    summaries.add(new DailyWeatherSummary(LocalDate.parse(result.getString("day")), result.getString("city"),
                            result.getDouble("avg_temperature"), result.getDouble("avg_humidity"),
                            result.getDouble("avg_pressure"), result.getInt("count")));
                }
            }
        }
        return summaries;
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
